# IAC 部署与切换

## 配置清单（待人工审批）

只复用既有基础设施，不新增基础服务实现。本轮未改基础服务仓库或现网配置。

| 所属配置 | 具体变更 | 验证与回退 |
| --- | --- | --- |
| base-k8s 数据库供应 | 新建 `iac_platform` 及独立最小权限账号 | 账号只能读写 IAC；回退停用新服务，保留库 |
| 服务编排 | 新增 `iac-platform`，应用 8050、管理 18090，绑定独立数据库与既有 Redis/Nacos | 健康、治理注册、监控；使用镜像 SHA/digest 回退 |
| Nacos/内部令牌 | IAC 为合法目标与签发方；IQC→IAC、IAC→组织/治理复用签名 RPC，IAC 消费 allowlist 为 `iqc-platform` | 错误受众/来源拒绝；IAC 会话全量查看 scope 使用 `iac:session:all`，只映射原来确有相应范围的调用方；恢复原允许列表 |
| 网关管理控制面 | `/api/iac/**` → `lb://iac-platform`，保留授权、审计等既有过滤器；内部接口不对浏览器开放 | 有角色可访问、无角色拒绝；撤回新路由 |
| 组织资源授权 | 激活新 `iac:*` 资源，按 `permission-mapping.json` 逐项复用旧资源的角色，不扩大角色范围 | 按角色验证创建/审批/查询/会话范围；撤回新增授权 |
| 治理配置 | 注册 `iac_*` 字典与 IAC 错误目录，激活应用中声明的 `iac-*` 限流场景 | 查询注册状态并重复调用测试，不能只验证首次成功 |
| 现有菜单 | 继续使用当前 IQC 管理端和既有智能体/会话页面；菜单描述中的 `iqc:*` UI 操作权限保持既有语义，新后端资源授权按映射单独绑定 | 页面路由、菜单角色与前端项目均保持原结构 |

IAC 的 `DATASOURCE_*` 必须指向新库账号；`JASYPT_ENCRYPTOR_PASSWORD` 及 `env:NAME` 引用的环境变量必须与导入历史引用匹配，才能继续解密和调用。勿把生产凭据提交到仓库。

`IAC_CONSUMER_ALLOWED_SERVICES` 默认为 `iqc-platform`。内部调用依赖框架已验证的令牌来源，而不是可伪造的请求头。外部 JWT 和共享服务注册令牌仍沿用现有方式配置。

IQC 的 Feign 客户端 `iqcIacConsumer` 需要为完整模型调用链设置足够的读取超时；默认值 600000ms，可通过 `IAC_RPC_READ_TIMEOUT_MILLIS` 调整。单个模型的超时/重试和多级备用链总耗时仍需与此预算匹配。框架的其他 RPC 客户端不受此配置影响。

## 数据迁移

IAC baseline 只包含七张表结构，不执行生产数据复制。IQC 已发布迁移不修改；切换后旧表保留供核对，但运行代码不再读写它们。

1. 备份 IQC 全库、角色映射、服务配置与镜像标识，验证备份可用。
2. 创建独立 IAC 数据库，使用 Flyway 11.18.0 对 `src/main/resources/db/migration/mysql` 执行 `migrate`、`validate`，再次 `migrate` 必须为零变更。
3. 暂停所有 IQC 实例的管理写入、审批、模型资产修改与测试聊天；停止受影响任务和调度，等待在途请求结束。网关停写不足以阻止直连或后台调用。
4. 确认新服务尚未写入，目标七表为空，再运行导入工具。凭据文件权限必须为 600，目标账号禁止访问 IQC 库。

```sh
python3 scripts/import-iqc-assets.py \
  --source-defaults-file /protected/iqc-client.cnf \
  --target-defaults-file /protected/iac-client.cnf \
  --source-database iqc_platform --target-database iac_platform \
  --confirm-source-writes-stopped --report /protected/iac-import-report.json
```

工具保留 ID、版本、时间、归属、配置和会话内容，校验源数据导出前后摘要、字段顺序与目标行数/摘要；目标非空拒绝覆盖。只替换 INSERT 表名，不替换 JSON、聊天或密钥中的文本。导入失败后应保持停写并检查目标库，不能忽略错误继续切流。

5. 启动 IAC，验证资源注册、凭据解密、模型连接、所有者/同组/管理员会话查询和非授权访问。
6. 更新全部 IQC 实例为消费 IAC 的版本；不存在旧 IQC 实例后再发布原管理前端的新构建并切换网关/授权。
7. 用真实角色验证创建→审批→任务引用、备用模型、MCP、模型用量、历史会话及再次聊天。确认没有旧 IQC 资产表读写后恢复业务。

回退时再次停写；IAC 接受过新数据后，旧库已经落后，禁止直接启动旧 IQC 管理入口。先对新增数据做经审批的反向迁移/恢复并核对，再恢复旧后端和前端。任何时候只保留一个资产写入方。

## 本地隔离验证

`IacMigrationMysqlIT` 显式检查环回端口 34309 和 `/private/tmp/iac-mysql-validation.*/data/`，随后只创建随机测试库。
设置 `IAC_MYSQL_VALIDATION_DIR`、`IAC_SOURCE_MIGRATIONS_DIR`（指向 IQC 隔离工作树的 mysql 迁移目录）、`IAC_MYSQL_CLIENT`、`IAC_MYSQL_DUMP` 后执行：

```sh
mvn -Dtest=IacMigrationMysqlIT test
```

CI 的独立 MySQL 服务验证新库 baseline、validate 与重复迁移；不依赖尚未支持 IAC 的 base-k8s 迁移服务清单。
