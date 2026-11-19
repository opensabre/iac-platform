package io.github.opensabre.iac.skill;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.opensabre.iac.governance.IacException;
import io.github.opensabre.iac.skill.dao.IacSkillMapper;
import io.github.opensabre.iac.skill.dao.IacSkillVersionMapper;
import io.github.opensabre.iac.skill.model.IacSkill;
import io.github.opensabre.iac.skill.model.IacSkillVersion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Manages reusable, credential-free Skill assets. */
@Service
public class IacSkillService {
    private final IacSkillMapper mapper;
    private final ObjectMapper objectMapper;
    private final IacSkillVersionMapper versionMapper;

    public IacSkillService(IacSkillMapper mapper, ObjectMapper objectMapper, IacSkillVersionMapper versionMapper) {
        this.mapper = mapper; this.objectMapper = objectMapper; this.versionMapper = versionMapper;
    }

    /** Lists Skills with enabled items first and newest items first. */
    public List<IacSkill> list() {
        return mapper.selectList(Wrappers.<IacSkill>lambdaQuery()
                .orderByAsc(IacSkill::getStatus).orderByDesc(IacSkill::getCreatedTime));
    }

    /** Creates an enabled Skill after validating its stable code and JSON schemas. */
    @Transactional
    public IacSkill create(String name, String code, String description, String instructions,
                           String inputSchemaJson, String outputSchemaJson) {
        validate(name, code, instructions, inputSchemaJson, outputSchemaJson);
        if (findByCode(code) != null) throw IacException.invalidState("Skill 编码已存在: " + code);
        IacSkill skill = new IacSkill();
        apply(skill, name, code, description, instructions, inputSchemaJson, outputSchemaJson);
        skill.setStatus("ENABLED"); skill.setVersionNo(1); mapper.insert(skill); snapshot(skill);
        return skill;
    }

    /** Updates a Skill and increments its configuration version. */
    @Transactional
    public IacSkill update(String id, String name, String description, String instructions,
                           String inputSchemaJson, String outputSchemaJson) {
        IacSkill skill = require(id);
        validate(name, skill.getCode(), instructions, inputSchemaJson, outputSchemaJson);
        apply(skill, name, skill.getCode(), description, instructions, inputSchemaJson, outputSchemaJson);
        skill.setVersionNo(skill.getVersionNo() + 1); mapper.updateById(skill); snapshot(skill);
        return skill;
    }

    /** Changes availability without deleting historical references. */
    @Transactional
    public IacSkill setEnabled(String id, boolean enabled) {
        IacSkill skill = require(id);
        String status = enabled ? "ENABLED" : "DISABLED";
        if (status.equals(skill.getStatus())) return skill;
        skill.setStatus(status); skill.setVersionNo(skill.getVersionNo() + 1); mapper.updateById(skill); snapshot(skill); return skill;
    }

    public List<IacSkillVersion> versions(String id) {
        require(id);
        return versionMapper.selectList(Wrappers.<IacSkillVersion>lambdaQuery()
                .eq(IacSkillVersion::getSkillId, id).orderByDesc(IacSkillVersion::getVersionNo));
    }

    @Transactional
    public IacSkill rollback(String id, int versionNo) {
        IacSkill skill = require(id);
        IacSkillVersion version = versionMapper.selectOne(Wrappers.<IacSkillVersion>lambdaQuery()
                .eq(IacSkillVersion::getSkillId, id).eq(IacSkillVersion::getVersionNo, versionNo));
        if (version == null) throw IacException.notFound("Skill 版本不存在: " + versionNo);
        apply(skill, version.getName(), skill.getCode(), version.getDescription(), version.getInstructions(),
                version.getInputSchemaJson(), version.getOutputSchemaJson());
        skill.setStatus(version.getStatus()); skill.setVersionNo(skill.getVersionNo() + 1);
        mapper.updateById(skill); snapshot(skill); return skill;
    }

    private void snapshot(IacSkill skill) {
        IacSkillVersion version = new IacSkillVersion(); version.setSkillId(skill.getId()); version.setVersionNo(skill.getVersionNo());
        version.setName(skill.getName()); version.setCode(skill.getCode()); version.setDescription(skill.getDescription());
        version.setInstructions(skill.getInstructions()); version.setInputSchemaJson(skill.getInputSchemaJson());
        version.setOutputSchemaJson(skill.getOutputSchemaJson()); version.setStatus(skill.getStatus()); versionMapper.insert(version);
    }

    private IacSkill findByCode(String code) {
        return mapper.selectOne(Wrappers.<IacSkill>lambdaQuery().eq(IacSkill::getCode, code));
    }
    private IacSkill require(String id) {
        IacSkill skill = mapper.selectById(id);
        if (skill == null) throw IacException.notFound("Skill 不存在: " + id);
        return skill;
    }
    private void validate(String name, String code, String instructions, String input, String output) {
        if (blank(name) || blank(code) || blank(instructions)) throw IacException.invalidArgument("Skill 名称、编码和指令不能为空");
        if (!code.matches("[A-Z][A-Z0-9_]{1,63}")) throw IacException.invalidArgument("Skill 编码必须为大写字母、数字或下划线");
        if (instructions.length() > 16000) throw IacException.invalidArgument("Skill 指令不能超过 16000 字符");
        validateJson(input, "输入 Schema"); validateJson(output, "输出 Schema");
    }
    private void validateJson(String value, String label) {
        if (blank(value)) return;
        try { if (!objectMapper.readTree(value).isObject()) throw IacException.invalidArgument(label + " 必须是 JSON 对象"); }
        catch (IacException exception) { throw exception; }
        catch (Exception exception) { throw IacException.invalidArgument(label + " 不是有效 JSON", exception); }
    }
    private static void apply(IacSkill skill, String name, String code, String description, String instructions, String input, String output) {
        skill.setName(name.trim()); skill.setCode(code.trim()); skill.setDescription(description);
        skill.setInstructions(instructions.trim()); skill.setInputSchemaJson(input); skill.setOutputSchemaJson(output);
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
