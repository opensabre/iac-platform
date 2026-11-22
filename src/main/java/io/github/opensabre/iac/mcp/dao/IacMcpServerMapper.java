package io.github.opensabre.iac.mcp.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.opensabre.iac.mcp.model.IacMcpServer;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface IacMcpServerMapper extends BaseMapper<IacMcpServer> { }
