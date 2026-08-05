package com.smartops.agent.logwatch.config;

import com.smartops.agent.logwatch.AlertPipelineService;
import com.smartops.infrastructure.logwatch.LogSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.mockito.InOrder;

/**
 * {@link LogWatchRunner} 单元测试。
 *
 * <p>覆盖：就绪后启动管线与全部采集源、关闭时先停采集源再停管线、
 * 单个采集源停止异常不影响其余停止。</p>
 *
 * @author smartops
 * @since 1.0.0
 */
class LogWatchRunnerTest {

    @Test
    @DisplayName("run 启动管线与全部采集源")
    void should_startAll_when_run() {
        LogSource source1 = mock(LogSource.class);
        LogSource source2 = mock(LogSource.class);
        AlertPipelineService pipeline = mock(AlertPipelineService.class);
        LogWatchRunner runner = new LogWatchRunner(List.of(source1, source2), pipeline);

        runner.run(null);

        verify(pipeline).start();
        verify(source1).start();
        verify(source2).start();
    }

    @Test
    @DisplayName("close 先停采集源再停管线")
    void should_stopSourcesBeforePipeline_when_close() {
        LogSource source = mock(LogSource.class);
        AlertPipelineService pipeline = mock(AlertPipelineService.class);
        LogWatchRunner runner = new LogWatchRunner(List.of(source), pipeline);

        runner.close();

        InOrder order = inOrder(source, pipeline);
        order.verify(source).stop();
        order.verify(pipeline).stop();
    }

    @Test
    @DisplayName("单个采集源停止异常不中断其余停止流程")
    void should_continueStopping_when_oneSourceStopFails() {
        LogSource bad = mock(LogSource.class);
        LogSource good = mock(LogSource.class);
        AlertPipelineService pipeline = mock(AlertPipelineService.class);
        doThrow(new IllegalStateException("stop failed")).when(bad).stop();
        LogWatchRunner runner = new LogWatchRunner(List.of(bad, good), pipeline);

        assertThatCode(runner::close).doesNotThrowAnyException();
        verify(good).stop();
        verify(pipeline).stop();
    }
}
