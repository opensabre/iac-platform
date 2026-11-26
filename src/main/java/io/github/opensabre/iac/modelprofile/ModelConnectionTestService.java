package io.github.opensabre.iac.modelprofile;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.iac.governance.IacException;
import io.github.opensabre.iac.modelprofile.dao.IacModelProfileMapper;
import io.github.opensabre.iac.modelprofile.model.IacModelProfile;
import io.github.opensabre.iac.result.llm.SnapshotChatModelRouter;
import io.github.opensabre.iac.result.llm.ModelFailure;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

/** Tests a persisted model profile without exposing provider credentials or raw failures. */
@Service
public class ModelConnectionTestService {
    private final IacModelProfileMapper mapper;
    private final SnapshotChatModelRouter modelRouter;
    private final ObjectMapper objectMapper;

    public ModelConnectionTestService(IacModelProfileMapper mapper, SnapshotChatModelRouter modelRouter,
                                      ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.modelRouter = modelRouter;
        this.objectMapper = objectMapper;
    }

    /** Sends a minimal prompt and reports only safe connection diagnostics. */
    public ConnectionTestResult test(String id) {
        IacModelProfile profile = mapper.selectById(id);
        if (profile == null) throw IacException.notFound("模型配置不存在: " + id);
        long started = System.nanoTime();
        try {
            var response = modelRouter.create(objectMapper.valueToTree(profile))
                .call(new Prompt("请只回复 OK"));
            if (response == null) throw new IllegalStateException("empty response");
            return new ConnectionTestResult(true, "连接成功", elapsedMillis(started), profile.getProvider(), profile.getModelName(), "OK");
        } catch (RuntimeException exception) {
            // Provider exceptions may contain request headers or remote payloads, so never return them to the client.
            ModelFailure diagnostic = ModelFailure.from(exception);
            return new ConnectionTestResult(false, diagnostic.message(), elapsedMillis(started),
                profile.getProvider(), profile.getModelName(), diagnostic.name());
        }
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    /** Safe model connection result returned to administrators. */
    public record ConnectionTestResult(boolean success, String message, long latencyMillis,
                                       String provider, String modelName, String code) { }
}
