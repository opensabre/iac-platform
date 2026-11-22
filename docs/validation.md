# 本地验证记录

- IAC：`mvn -o -q -Dtest=*Test,IacMigrationMysqlIT clean test`，15 组、60 测试，零失败/错误/跳过。
- IQC：`mvn -o -q clean test`，85 组、744 测试，零失败/错误/跳过；已检查新编译产物无迁出的资产 Mapper/控制器。
- 现有管理前端：全量 43 组、243 测试通过；新增 IAC/IQC API 归属测试 3 项通过；生产构建通过。
- MySQL 8.0.34：使用 `/private/tmp/iac-mysql-validation.*` 独立数据目录、环回端口 34309。源由当前 IQC 完整迁移创建；IAC 新库 baseline、validate、重复 migrate 均通过。
- 七表导入：保留中文/emoji、长会话、ID、版本、归属、配置、加密引用及包含旧表名的业务文本；逐表行数及 SHA-256 摘要一致；未确认停写、目标非空时拒绝写入。
- 迁移命名检查、Python 语法检查通过；33 个公开资源权限编码唯一，且覆盖权限迁移清单。
- CI 镜像标签脚本：main、正式版本、预发布、非法标签、版本不匹配五种路径均通过本地契约验证；未运行远端 CI。

独立代码审查不可用：`code-review` 要求的 code-reviewer 与 architect 两个角色均因当前账号不支持其配置模型而启动失败。本记录不代表独立审查通过或允许合并。

本轮未修改基础服务/框架，未推送、合并、部署或迁移线上数据。共享配置与生产切换见待审批部署清单。
