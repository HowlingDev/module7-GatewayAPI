package com.example;

import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/apigateway")
public class ApiGatewayController {

    @Setter
    @Value("${user.service.url}")
    private String userServiceUrl;

    @Setter
    @Value("${notification.service.url}")
    private String notificationServiceUrl;

}
