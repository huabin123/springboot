package com.huabin.ratelimiter.bean.resp;

import lombok.Builder;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author huabin
 * @DateTime 2023-01-11 13:43
 * @Desc
 */

@Data
@Builder
@Accessors(chain = true)
public class RateLimiterTestResp {

    private String name;

}
