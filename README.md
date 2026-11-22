# iac-platform

OpenSabre 智能体后端。管理智能体及审批版本、模型配置、MCP、Skill、测试聊天与会话历史。
现有 `iqc-platform-admin` 继续提供界面；本项目没有独立前端或 worker。

使用 Framework 1.1.4、Java 21+、MySQL 8 与现有 Redis/Nacos/组织/治理服务。

```sh
mvn verify
python3 scripts/check-migrations.py
```

应用端口 8050，管理端口 18090。管理 API `/api/iac/**`，业务消费接口 `/internal/iac/**`。
复用 OpenSabre JWT、签名内部令牌、资源注册、数据范围、审计和治理能力。

- [边界与拆分设计](docs/extraction-design.md)
- [部署与数据切换](docs/deployment.md)
- [待审批权限映射](docs/permission-mapping.json)

本地验证不等同于发布。共享服务配置变更和线上数据切换需要按项目规则审批。
