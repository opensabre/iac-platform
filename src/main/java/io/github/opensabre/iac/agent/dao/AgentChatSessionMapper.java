package io.github.opensabre.iac.agent.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.opensabre.iac.agent.model.AgentChatSession;
import org.apache.ibatis.annotations.Mapper;

/** IAC-owned persistence for agent chat sessions. */
@Mapper
public interface AgentChatSessionMapper extends BaseMapper<AgentChatSession> { }
