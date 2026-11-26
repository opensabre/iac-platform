# 智能体对外调用与会话查询

## 接入与凭据

授权服务中的 OAuth 客户端负责凭据签发、轮换和吊销。为每个接入应用使用专用客户端，授权方式选择 `client_credentials`，添加 `iac.invoke` scope。通过授权服务管理客户端密钥。轮换或吊销期间，可在 IAC「接入应用」立即停用该应用，已签发令牌也会被拒绝。重新启用前，应等待旧令牌过期或完成对应客户端的撤销流程。

在现有管理前端的「智能体管理 → 接入应用」登记同一个 OAuth 客户端 ID。授权绑定到明确的智能体 ID 和版本号。选择「启用」并配置每分钟、每日请求上限。配额按 UTC 自然分钟和自然日计算。IAC 按应用原子预留两个用量窗口，提供跨实例的一致限额和管理端用量读数。通过授权后尝试执行的失败请求也占用额度。

## 调用示例

从对应环境的 OAuth 令牌端点获取访问令牌，调用智能体时显式提供版本。输入限 32,000 字符。

```sh
curl -sS --fail-with-body -u "$IAC_CLIENT_ID:$IAC_CLIENT_SECRET" \
  --data-urlencode 'grant_type=client_credentials' \
  --data-urlencode 'scope=iac.invoke' "$OAUTH_TOKEN_URL"

curl -sS --fail-with-body "$IAC_BASE_URL/api/iac/v1/agents/$IAC_AGENT_ID/invoke" \
  -H "Authorization: Bearer $IAC_ACCESS_TOKEN" \
  -H 'Content-Type: application/json' \
  --data '{"versionNo":3,"input":"请用一句话介绍你的能力"}'
```

成功时返回 OpenSabre 响应，`code` 为 `000000`，`data` 包含 `requestId`、`sessionId`、实际执行的 `versionNo`、`content`、`model` 和 `latencyMillis`。当前接口每次执行一个独立轮次。

| HTTP 状态 | 错误码 | 含义 |
|---|---|---|
| 400 | IAC-1003 | 参数非法 |
| 401 / 403 | IAC-1005 或网关鉴权响应 | 令牌无效、scope 缺失、应用停用或版本未授权 |
| 404 | IAC-1004 | 已授权的智能体或版本不存在 |
| 409 | IAC-1006 | 智能体或版本当前不可调用 |
| 429 | IAC-1007 | 调用配额已用尽，或登记的授权在执行前发生变更 |
| 502 | IAC-1006 | 智能体执行未完成，响应头 `X-IAC-Request-Id` 可用于查询 |

## 会话与执行明细

- 「智能体会话」保留测试对话和成功的外部对话。展开对应消息可查看请求 ID、模型候选尝试、MCP 连接与工具执行、耗时、错误类型和供应商返回的 Token。会话正文使用独立资源权限并留访问审计。审计只记录会话标识，管理页面对可识别的凭据、邮箱和手机号做脱敏展示。
- 同一页面的「按标识查询执行」可使用请求 ID，或 IQC 任务 ID，查询相应的调用。IQC 的任务执行、业务结果和原始会话继续在 IQC 查看。IAC 的执行明细持久化模型和工具元数据。
- 应用调用和测试聊天按轮次记录。候选模型回退各自生成步骤。供应商 SDK 自身重试次数取决于模型配置，记录粒度为候选尝试。Token 缺失时保留为空。
- 部署可启用 `iac.history.retention.enabled=true`，默认保留 90 天，并可在 `iac.history.retention.days` 配置保留期限、在 `iac.history.retention.cron` 配置运行时段。每次最多清理 500 条会话和 500 条执行，按最后会话活动及调用开始时间计算。

## 发布配置

先运行 IAC 自有 Flyway 迁移，再发布应用和原管理前端。共享配置需按项目规则审批。只为 `POST /api/iac/v1/agents/{agentId}/invoke` 配置网关的 `AUTHENTICATED` 访问模式并转发 Bearer 令牌。IAC 再校验 `iac.invoke`、客户端标识与 audience、应用启用状态、版本授权和用量配额。其它管理接口继续走资源授权。管理菜单和按钮权限按随应用交付的权限映射配置。

发布验证应分别使用 OAuth 应用令牌、普通会话查询角色、正文查看角色和 IQC 任务触发两条链路，并检查部署配置确实将应用令牌的访问范围限制在对外调用入口。
