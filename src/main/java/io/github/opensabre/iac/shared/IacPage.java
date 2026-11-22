package io.github.opensabre.iac.shared;

import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/** IAC list response contract; keeps frontend pagination independent from MyBatis-Plus internals. */
public record IacPage<T>(List<T> records, long current, long size, long total) {
    public static <T> IacPage<T> from(IPage<T> page) {
        return new IacPage<>(page.getRecords(), page.getCurrent(), page.getSize(), page.getTotal());
    }
}
