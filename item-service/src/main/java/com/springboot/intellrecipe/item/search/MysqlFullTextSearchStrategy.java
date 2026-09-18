package com.springboot.intellrecipe.item.search;

import cn.hutool.core.bean.BeanUtil;
import com.springboot.intellrecipe.common.entity.Ingredient;
import com.springboot.intellrecipe.item.es.document.IngredientDoc;
import com.springboot.intellrecipe.item.mapper.IngredientMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 二级检索策略：MySQL ngram 全文索引。
 * <p>
 * 这是本次升级的核心——把原来的 <code>LIKE '%单字%'</code> 换成
 * <code>MATCH(name, description) AGAINST(... IN BOOLEAN MODE)</code>。
 *
 * <h3>两者的本质区别</h3>
 * <pre>
 *   LIKE '%番茄%'     → 前导通配符，B+ 树无法定位起点 → 全表扫描 O(n)，无相关度
 *   MATCH ... AGAINST → 查 FULLTEXT 倒排索引，直接定位 posting list → O(匹配文档数)，
 *                       且 MySQL 会按 TF-IDF 计算相关度评分，可按 score 排序
 * </pre>
 *
 * <h3>关键词改写：应用层模拟 ngram 切分</h3>
 * MySQL 服务端用 <code>ngram_token_size=2</code> 把文档切成二元 token
 * （"番茄炒蛋" → "番茄" "茄炒" "炒蛋"）。
 * 那么查询串也必须用<b>同样的切分规则</b>才能命中对等的 token，
 * 否则会出现"文档里有这个词，但 token 对不上所以搜不到"。
 * 因此这里在应用层做相同的 bigram 切分，并用 <code>+</code> 连接成 AND 语义：
 * <pre>
 *   "番茄炒蛋" → "+番茄 +茄炒 +炒蛋"
 * </pre>
 * 这个 AND 语义恰好等价于"文档必须包含完整的番茄炒蛋"，与 ES 侧的
 * <code>Operator.AND</code> 行为对齐，降级后用户的搜索体验不会明显劣化。
 *
 * <h3>索引缺失的自动降级</h3>
 * 如果运维没有执行建索引的 migration，MATCH 会直接报错。
 * 这里捕获该错误后把策略<b>永久置为不可用</b>，后续请求直接走 LIKE 兜底，
 * 不会每次都抛一次异常再降级（那又是"每次都付出失败代价"的老问题）。
 */
@Component
public class MysqlFullTextSearchStrategy implements IngredientSearchStrategy {

    private static final Logger log = LoggerFactory.getLogger(MysqlFullTextSearchStrategy.class);

    private static final int SIZE = 20;

    @Resource
    private IngredientMapper ingredientMapper;

    /** 必须与 MySQL 的 ngram_token_size 保持一致，否则查询 token 与索引 token 对不上 */
    @Value("${item.search.ngram-token-size:2}")
    private int ngramTokenSize;

    /** 索引不存在时置 true，永久关闭本策略 */
    private volatile boolean disabled = false;

    @Override
    public String name() {
        return "MySQL-FULLTEXT";
    }

    @Override
    public int order() {
        return 20;
    }

    @Override
    public boolean available(String key) {
        if (disabled) {
            return false;
        }
        // 关键词清洗后为空、或短于 ngram_token_size → 全文索引命中不了，交给 LIKE 策略
        String q = toBooleanModeQuery(key, ngramTokenSize);
        return q != null && !q.isEmpty();
    }

    @Override
    public List<IngredientDoc> search(String key) {
        String q = toBooleanModeQuery(key, ngramTokenSize);
        if (q == null || q.isEmpty()) {
            throw new IllegalStateException("关键词不适用于全文索引：" + key);
        }

        try {
            List<Ingredient> list = ingredientMapper.searchByFullText(q, SIZE);
            if (list == null || list.isEmpty()) {
                return Collections.emptyList();
            }
            log.debug("[Search:MySQL-FULLTEXT] key={} → q={}, hits={}", key, q, list.size());
            return list.stream()
                    .map(i -> BeanUtil.copyProperties(i, IngredientDoc.class))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            if (isFullTextIndexMissing(e)) {
                disabled = true;
                log.error("[Search:MySQL-FULLTEXT] 未检测到 ngram 全文索引，本策略已永久关闭，后续走 LIKE 兜底。" +
                        "请执行 docker/mysql/init/migration/20260917_ingredient_fulltext.sql", e);
            } else {
                log.warn("[Search:MySQL-FULLTEXT] 全文检索失败，key={}", key, e);
            }
            throw e;
        }
    }

    /**
     * 把用户关键词改写为 BOOLEAN MODE 查询串。
     * <p>
     * 处理步骤：
     * <ol>
     *   <li>中文之间的空白直接去掉（"鸡 蛋" → "鸡蛋"），其余空白保留（英文多词要能分开）</li>
     *   <li>清洗：只保留中日韩文字、字母、数字，其余（含 BOOLEAN MODE 的操作符
     *       {@code + - * ~ < > ( ) " @}）变成空白作为分隔符，防止用户构造特殊查询串</li>
     *   <li>按空白切成多个 term</li>
     *   <li>纯 ASCII 的 term（英文/数字）不切 ngram，整体作为一个 token</li>
     *   <li>中文 term 按 ngramTokenSize 滑动切分，得到一组 token</li>
     *   <li>所有 token 前置 {@code +}，形成 AND 语义</li>
     * </ol>
     * <p>
     * 为何第 1 步只在中文之间去空白：英文多词 "tomato egg" 中间的空格是有语义的，
     * 必须保留；而中文里 "鸡 蛋" 通常是输入习惯，用户想找的就是"鸡蛋"。
     * 反过来，括号这类符号要<b>保留其分隔语义</b>——"牛肉(进口)" 应切成 {@code [牛肉][进口]}，
     * 若把符号直接删掉变成"牛肉进口"，bigram 会多产出索引里根本不存在的 {@code 肉进}，反而搜不到。
     *
     * @return BOOLEAN MODE 查询串；若关键词过短（不足以构成任何 token）返回 null
     */
    static String toBooleanModeQuery(String key, int n) {
        if (key == null || n <= 0) {
            return null;
        }
        // 1. 中文之间的空白视为无意义，直接去掉。用断言限定只在【中文 空白 中文】时生效，
        //    避免误伤英文多词（"tomato egg" 要切成两个 term）
        String joined = key.replaceAll("(?<=[\\u4e00-\\u9fa5\\u3040-\\u30ff])\\s+(?=[\\u4e00-\\u9fa5\\u3040-\\u30ff])", "");
        // 2. 清洗：其余非文字字符（含 BOOLEAN MODE 操作符）→ 空白，作为分隔符
        String cleaned = joined.replaceAll("[^\\u4e00-\\u9fa5\\u3040-\\u30ffa-zA-Z0-9\\s]", " ")
                              .trim();
        if (cleaned.isEmpty()) {
            return null;
        }

        Set<String> tokens = new LinkedHashSet<>();
        for (String term : cleaned.split("\\s+")) {
            if (term.isEmpty()) {
                continue;
            }
            if (isAscii(term)) {
                // 英文/数字：整体作为一个词，ngram 切分对英文无意义且会拖垮精度
                tokens.add(term);
                continue;
            }
            if (term.length() < n) {
                // 短于 token_size，无法构成任何 ngram token，跳过
                continue;
            }
            // 滑动窗口切 n-gram
            for (int i = 0; i + n <= term.length(); i++) {
                tokens.add(term.substring(i, i + n));
            }
        }

        if (tokens.isEmpty()) {
            return null;
        }

        // AND 语义：每个 token 都必须出现
        List<String> parts = new ArrayList<>(tokens.size());
        for (String t : tokens) {
            parts.add("+" + t);
        }
        return String.join(" ", parts);
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) > 127) {
                return false;
            }
        }
        return true;
    }

    /** 判断是否为"FULLTEXT 索引不存在"导致的失败 */
    private static boolean isFullTextIndexMissing(Throwable t) {
        Throwable cur = t;
        int depth = 0;
        while (cur != null && depth++ < 10) {
            String msg = cur.getMessage();
            if (msg != null && (msg.contains("Can't find FULLTEXT index")
                    || msg.contains("fulltext")
                    || msg.contains("FULLTEXT"))) {
                return true;
            }
            cur = cur.getCause();
        }
        return false;
    }
}
