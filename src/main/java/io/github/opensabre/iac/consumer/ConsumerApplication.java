package io.github.opensabre.iac.consumer;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** IAC policy for an OAuth client whose credentials are managed by OpenSabre Authorization. */
@Data
@TableName("iac_consumer_application")
public class ConsumerApplication {
    @TableId(type = IdType.INPUT)
    private String clientId;
    private String displayName;
    private Boolean enabled;
    private Integer perMinute;
    private Integer perDay;
    private Long minuteWindow;
    private Integer minuteUsed;
    private Long dayWindow;
    private Integer dayUsed;
    private String grantsJson;
}
