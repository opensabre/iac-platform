package io.github.opensabre.iac.invocation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.opensabre.iac.agent.dao.AgentChatSessionMapper;
import io.github.opensabre.iac.agent.model.AgentChatSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

/** Removes expired IAC history in bounded batches when a deployment enables its retention policy. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "iac.history.retention", name = "enabled", havingValue = "true")
@Slf4j
public class AgentHistoryRetention {
    private final AgentChatSessionMapper sessions;
    private final AgentInvocationMapper invocations;
    private final int days;

    public AgentHistoryRetention(AgentChatSessionMapper sessions, AgentInvocationMapper invocations,
                                 @Value("${iac.history.retention.days:90}") int days) {
        if (days < 1) throw new IllegalArgumentException("IAC 历史保留天数必须大于零");
        this.sessions = sessions;
        this.invocations = invocations;
        this.days = days;
    }

    /** Retention is based on last session activity and the execution start, respectively. */
    @Scheduled(cron = "${iac.history.retention.cron:0 35 3 * * *}")
    public void expire() {
        var cutoff = LocalDateTime.now().minusDays(days);
        int sessionCount = sessions.delete(Wrappers.<AgentChatSession>lambdaQuery()
                .lt(AgentChatSession::getLastMessageTime, cutoff).last("LIMIT 500"));
        int invocationCount = invocations.delete(Wrappers.<AgentInvocation>lambdaQuery()
                .lt(AgentInvocation::getStartedTime, cutoff).last("LIMIT 500"));
        if (sessionCount > 0 || invocationCount > 0)
            log.info("IAC history retention removed {} sessions and {} executions", sessionCount, invocationCount);
    }
}
