package com.springboot.intellrecipe.diet.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.springboot.intellrecipe.diet.dto.AddEntryDTO;
import com.springboot.intellrecipe.diet.dto.TodayDietVO;
import com.springboot.intellrecipe.diet.entity.DietLog;
import com.springboot.intellrecipe.diet.mapper.DietLogMapper;
import com.springboot.intellrecipe.diet.service.DietService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DietServiceImpl implements DietService {

    private final DietLogMapper dietLogMapper;

    @Override
    public TodayDietVO getToday(Long userId) {
        LocalDate today = LocalDate.now();
        List<DietLog> logs = dietLogMapper.selectList(
                new LambdaQueryWrapper<DietLog>()
                        .eq(DietLog::getUserId, userId)
                        .eq(DietLog::getLogDate, today)
                        .orderByAsc(DietLog::getCreateTime)
        );

        List<TodayDietVO.EntryVO> entries = logs.stream()
                .map(TodayDietVO.EntryVO::from)
                .collect(Collectors.toList());

        BigDecimal total = entries.stream()
                .map(TodayDietVO.EntryVO::getCalories)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(1, RoundingMode.HALF_UP);

        TodayDietVO vo = new TodayDietVO();
        vo.setDate(today);
        vo.setTotalCalories(total);
        vo.setEntries(entries);
        return vo;
    }

    @Override
    public Long addEntry(Long userId, AddEntryDTO dto) {
        if (dto.getIngredientId() == null) throw new IllegalArgumentException("食材ID不能为空");
        if (dto.getGrams() == null || dto.getGrams().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("克数必须大于0");
        }

        DietLog log = new DietLog();
        log.setUserId(userId);
        log.setIngredientId(dto.getIngredientId());
        log.setIngredientName(dto.getIngredientName() != null ? dto.getIngredientName() : "未知食材");
        log.setCaloriesPer100g(dto.getCaloriesPer100g() != null ? dto.getCaloriesPer100g() : BigDecimal.ZERO);
        log.setGrams(dto.getGrams());
        log.setMealType(dto.getMealType() != null ? dto.getMealType() : 0);
        log.setLogDate(LocalDate.now());
        log.setCreateTime(LocalDateTime.now());

        dietLogMapper.insert(log);
        return log.getId();
    }

    @Override
    public void removeEntry(Long userId, Long entryId) {
        DietLog log = dietLogMapper.selectById(entryId);
        if (log == null) throw new IllegalArgumentException("记录不存在");
        if (!log.getUserId().equals(userId)) throw new IllegalArgumentException("无权删除");
        dietLogMapper.deleteById(entryId);
    }
}
