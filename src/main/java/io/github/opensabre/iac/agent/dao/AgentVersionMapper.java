package io.github.opensabre.iac.agent.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.opensabre.iac.agent.model.AgentVersion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentVersionMapper extends BaseMapper<AgentVersion> { }
