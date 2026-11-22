package io.github.opensabre.iac.modelprofile;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.opensabre.iac.governance.IacException;
import io.github.opensabre.iac.governance.IacModelProvider;
import io.github.opensabre.iac.modelprofile.dao.IacModelProfileMapper;
import io.github.opensabre.iac.modelprofile.model.IacModelProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.jasypt.encryption.StringEncryptor;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

/** Manages model profiles while keeping provider credentials outside IAC data. */
@Service
public class IacModelProfileService {
    private final IacModelProfileMapper mapper;
    private final StringEncryptor encryptor;
    public IacModelProfileService(IacModelProfileMapper mapper, StringEncryptor encryptor) { this.mapper = mapper; this.encryptor = encryptor; }
    public List<IacModelProfile> list() { return mapper.selectList(Wrappers.<IacModelProfile>lambdaQuery().orderByAsc(IacModelProfile::getStatus).orderByDesc(IacModelProfile::getCreatedTime)); }

    /** Creates an enabled model profile after provider and connection validation. */
    @Transactional
    public IacModelProfile create(ModelCommand command) {
        validate(command);
        if (findByCode(command.code()) != null) throw IacException.invalidState("模型配置编码已存在: " + command.code());
        IacModelProfile profile = new IacModelProfile(); apply(profile, command); profile.setStatus("ENABLED"); profile.setVersionNo(1); mapper.insert(profile); return profile;
    }
    /** Updates model parameters but keeps the stable profile code. */
    @Transactional
    public IacModelProfile update(String id, ModelCommand command) {
        IacModelProfile profile = require(id);
        if (blank(command.secretRef())) command = command.withSecretRef(profile.getSecretRef());
        command = command.withCode(profile.getCode()); validate(command); apply(profile, command);
        profile.setVersionNo(profile.getVersionNo() + 1); mapper.updateById(profile); return profile;
    }
    @Transactional
    public IacModelProfile setEnabled(String id, boolean enabled) { IacModelProfile profile = require(id); profile.setStatus(enabled ? "ENABLED" : "DISABLED"); mapper.updateById(profile); return profile; }

    private void validate(ModelCommand value) {
        if (blank(value.name()) || blank(value.code()) || blank(value.provider()) || blank(value.modelName())) throw IacException.invalidArgument("模型配置名称、编码、供应商和模型名称不能为空");
        if (!value.code().matches("[A-Z][A-Z0-9_]{1,63}")) throw IacException.invalidArgument("模型配置编码必须为大写字母、数字或下划线");
        if (Arrays.stream(IacModelProvider.values()).noneMatch(item -> item.value().equals(value.provider()))) throw IacException.invalidArgument("不支持的模型供应商");
        if (!"OLLAMA".equals(value.provider()) && blank(value.secretRef())) throw IacException.invalidArgument("非本地模型必须填写 API Key");
        if (!blank(value.endpoint())) try { URI uri = URI.create(value.endpoint()); if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) throw new IllegalArgumentException(); } catch (Exception exception) { throw IacException.invalidArgument("模型 Endpoint 必须是有效的 HTTP(S) URL"); }
        if (value.temperature() == null || value.temperature() < 0 || value.temperature() > 2) throw IacException.invalidArgument("模型温度必须在 0 到 2 之间");
        if (value.timeoutSeconds() == null || value.timeoutSeconds() < 1 || value.timeoutSeconds() > 600) throw IacException.invalidArgument("模型超时时间必须在 1 到 600 秒之间");
        if (value.maxRetries() == null || value.maxRetries() < 0 || value.maxRetries() > 5) throw IacException.invalidArgument("模型重试次数必须在 0 到 5 之间");
    }
    private IacModelProfile findByCode(String code) { return mapper.selectOne(Wrappers.<IacModelProfile>lambdaQuery().eq(IacModelProfile::getCode, code)); }
    private IacModelProfile require(String id) { IacModelProfile profile = mapper.selectById(id); if (profile == null) throw IacException.notFound("模型配置不存在: " + id); return profile; }
    private void apply(IacModelProfile target, ModelCommand source) { target.setName(source.name().trim()); target.setCode(source.code().trim()); target.setDescription(source.description()); target.setProvider(source.provider()); target.setModelName(source.modelName().trim()); target.setEndpoint(source.endpoint()); target.setSecretRef(encryptSecret(source.secretRef())); target.setTemperature(source.temperature()); target.setTimeoutSeconds(source.timeoutSeconds()); target.setMaxRetries(source.maxRetries()); }
    private String encryptSecret(String value) { if (blank(value) || value.startsWith("env:") || value.startsWith("ENC(")) return value; return "ENC(" + encryptor.encrypt(value) + ")"; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    public record ModelCommand(String name, String code, String description, String provider, String modelName,
                               String endpoint, String secretRef, Double temperature, Integer timeoutSeconds, Integer maxRetries) {
        ModelCommand withCode(String stableCode) { return new ModelCommand(name, stableCode, description, provider, modelName, endpoint, secretRef, temperature, timeoutSeconds, maxRetries); }
        ModelCommand withSecretRef(String value) { return new ModelCommand(name, code, description, provider, modelName, endpoint, value, temperature, timeoutSeconds, maxRetries); }
    }
}
