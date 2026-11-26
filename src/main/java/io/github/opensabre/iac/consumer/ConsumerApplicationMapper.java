package io.github.opensabre.iac.consumer;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** Atomic fixed-window quota reservation across IAC instances. */
@Mapper
public interface ConsumerApplicationMapper extends BaseMapper<ConsumerApplication> {
    @Update("""
            UPDATE iac_consumer_application
               SET minute_used = CASE WHEN minute_window = #{minute} THEN minute_used + 1 ELSE 1 END,
                   minute_window = #{minute},
                   day_used = CASE WHEN day_window = #{day} THEN day_used + 1 ELSE 1 END,
                   day_window = #{day}
             WHERE client_id = #{clientId} AND enabled = TRUE AND CAST(grants_json AS BINARY) = CAST(#{grants} AS BINARY)
               AND (minute_window IS NULL OR minute_window < #{minute}
                    OR (minute_window = #{minute} AND minute_used < per_minute))
               AND (day_window IS NULL OR day_window < #{day}
                    OR (day_window = #{day} AND day_used < per_day))
            """)
    int reserve(@Param("clientId") String clientId, @Param("grants") String grants,
                @Param("minute") long minute, @Param("day") long day);
}
