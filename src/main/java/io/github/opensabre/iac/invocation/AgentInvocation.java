package io.github.opensabre.iac.invocation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** Metadata for one execution. Prompts, replies, credentials and raw provider errors stay out of this table. */
@Data
@TableName("iac_agent_invocation")
public class AgentInvocation {
    @TableId(type = IdType.INPUT)
    private String requestId;
    private String source;
    private String caller;
    private String agentId;
    private Integer versionNo;
    private String sessionId;
    private String businessReference;
    private String owner;
    private String ownerGroupId;
    private String status;
    private String errorCode;
    private String modelName;
    private Long durationMs;
    private Integer inputTokens;
    private Integer outputTokens;
    private String stepsJson;
    private LocalDateTime startedTime;
}
