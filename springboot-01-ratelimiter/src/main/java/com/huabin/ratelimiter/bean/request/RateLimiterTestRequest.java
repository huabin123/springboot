package com.huabin.ratelimiter.bean.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * @Author huabin
 * @DateTime 2023-01-11 13:43
 * @Desc
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RateLimiterTestRequest {

    private String name;

}
