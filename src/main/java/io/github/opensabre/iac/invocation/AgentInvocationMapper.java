package io.github.opensabre.iac.invocation;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** IAC execution history persistence. */
@Mapper
public interface AgentInvocationMapper extends BaseMapper<AgentInvocation> {
}
