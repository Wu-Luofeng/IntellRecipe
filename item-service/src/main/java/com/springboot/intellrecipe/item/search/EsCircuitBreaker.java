package com.springboot.intellrecipe.item.search;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 轻量级熔断器（ES 搜索专用）。
 * <p>
 * 为什么需要它：原来的实现是"try ES → 超时/异常 → catch → 降级"。
 * 这种方式在 ES 真正故障期间是灾难性的——<b>每一个</b>请求都要先卡满超时时间才能降级，
 * Tomcat 的 200 个线程很快被全部占满，整个 item-service 失去响应，
 * 从"搜索变慢"演变成"整个服务不可用"，这就是典型的故障放大（雪崩）。
 * <p>
 * 熔断器解决的是：一旦确认下游不可靠，<b>后续请求直接短路走降级，一次都不再尝试</b>，
 * 把线程资源还给业务；同时定期放一个探测请求去试探下游是否已恢复。
 * <p>
 * 三态模型（与 Resilience4j / Hystrix / Sentinel 一致）：
 * <pre>
 *   CLOSED ──失败率超阈值──► OPEN ──冷却期结束──► HALF_OPEN
 *     ▲                                              │
 *     └──────────探测成功────────────────────────────┘
 *                        └── 探测失败 ──► 回到 OPEN
 * </pre>
 * <p>
 * 这里手写而不引入 Resilience4j，是为了让"滑动窗口计数 + 状态机"的逻辑显式可见；
 * 生产项目直接用 Resilience4j 即可。
 */
public class EsCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(EsCircuitBreaker.class);

    public enum State { CLOSED, OPEN, HALF_OPEN }

    /** 统计窗口长度（毫秒）：窗口内滚动统计成功/失败 */
    private final long windowMs;
    /** 窗口内最少调用数：样本太少时不做熔断判断，避免"1 次失败就熔断" */
    private final int minCalls;
    /** 失败率阈值（百分比）：超过则熔断 */
    private final int failureRateThreshold;
    /** OPEN 状态持续时长（毫秒）：冷却期，期间一律短路 */
    private final long openWaitMs;
    /** HALF_OPEN 状态允许通过的探测请求数 */
    private final int halfOpenPermits;

    private final ReentrantLock lock = new ReentrantLock();

    private volatile State state = State.CLOSED;
    private volatile long openUntil = 0L;
    private volatile long windowStart = System.currentTimeMillis();

    private int windowTotal = 0;
    private int windowFail = 0;
    private int halfOpenPassed = 0;

    public EsCircuitBreaker(long windowMs, int minCalls, int failureRateThreshold,
                            long openWaitMs, int halfOpenPermits) {
        this.windowMs = windowMs;
        this.minCalls = minCalls;
        this.failureRateThreshold = failureRateThreshold;
        this.openWaitMs = openWaitMs;
        this.halfOpenPermits = halfOpenPermits;
    }

    /** 默认配置：10s 窗口、至少 10 次调用、失败率 > 50% 熔断、冷却 30s、半开放行 3 个探测 */
    public static EsCircuitBreaker defaultConfig() {
        return new EsCircuitBreaker(10_000L, 10, 50, 30_000L, 3);
    }

    /**
     * 是否允许发起真实请求。
     * 注意这里是<b>非阻塞</b>的——拿不到许可立刻返回 false，调用方直接走降级，绝不等待。
     */
    public boolean allowRequest() {
        long now = System.currentTimeMillis();

        if (state == State.OPEN) {
            if (now >= openUntil) {
                // 冷却期结束，转半开，放行有限个探测请求
                transitionToHalfOpen();
            } else {
                return false;
            }
        }

        if (state == State.HALF_OPEN) {
            // 只允许固定数量的探测请求通过
            return halfOpenPassed < halfOpenPermits;
        }

        return true;
    }

    public void recordSuccess() {
        lock.lock();
        try {
            if (state == State.HALF_OPEN) {
                // 半开状态下探测成功 → 认为下游已恢复，关闭熔断并重置窗口
                log.info("[CircuitBreaker] 探测成功，下游已恢复，熔断器 CLOSED");
                resetWindow();
                state = State.CLOSED;
                halfOpenPassed = 0;
                return;
            }
            rollWindowIfNeeded();
            windowTotal++;
        } finally {
            lock.unlock();
        }
    }

    public void recordFailure() {
        lock.lock();
        try {
            if (state == State.HALF_OPEN) {
                // 半开状态下探测仍然失败 → 回到 OPEN，重新计时冷却
                log.warn("[CircuitBreaker] 探测失败，下游未恢复，熔断器回到 OPEN");
                state = State.OPEN;
                openUntil = System.currentTimeMillis() + openWaitMs;
                halfOpenPassed = 0;
                return;
            }
            rollWindowIfNeeded();
            windowTotal++;
            windowFail++;

            if (windowTotal >= minCalls) {
                int rate = windowFail * 100 / windowTotal;
                if (rate > failureRateThreshold) {
                    log.warn("[CircuitBreaker] 失败率 {}% > {}%，熔断器 OPEN，冷却 {}ms",
                            rate, failureRateThreshold, openWaitMs);
                    state = State.OPEN;
                    openUntil = System.currentTimeMillis() + openWaitMs;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /** 在 HALF_OPEN 下计数已放行的探测请求 */
    public void markHalfOpenPassed() {
        if (state == State.HALF_OPEN) {
            halfOpenPassed++;
        }
    }

    private void transitionToHalfOpen() {
        lock.lock();
        try {
            if (state == State.OPEN) {
                log.info("[CircuitBreaker] 冷却期结束，进入 HALF_OPEN，放行 {} 个探测请求", halfOpenPermits);
                state = State.HALF_OPEN;
                halfOpenPassed = 0;
            }
        } finally {
            lock.unlock();
        }
    }

    /** 窗口到期则整体重置，实现"滑动窗口"里最简单的"滚动重置"语义 */
    private void rollWindowIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - windowStart >= windowMs) {
            windowStart = now;
            windowTotal = 0;
            windowFail = 0;
        }
    }

    private void resetWindow() {
        windowStart = System.currentTimeMillis();
        windowTotal = 0;
        windowFail = 0;
    }

    public State getState() {
        return state;
    }

    public boolean isClosed() {
        return state == State.CLOSED;
    }
}
