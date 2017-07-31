/*
 *  Copyright 2017 Curity AB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.curity.identityserver.plugin.sms.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.curity.identityserver.plugin.sms.rest.config.SmsRestPluginConfiguration;
import se.curity.identityserver.sdk.errors.ErrorCode;
import se.curity.identityserver.sdk.service.ExceptionFactory;
import se.curity.identityserver.sdk.service.HttpClient.HttpClientException;
import se.curity.identityserver.sdk.service.Json;
import se.curity.identityserver.sdk.service.WebServiceClient;
import se.curity.identityserver.sdk.service.sms.SmsSender;
import se.curity.identityserver.sdk.http.ContentType;
import se.curity.identityserver.sdk.http.HttpRequest;
import se.curity.identityserver.sdk.http.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class RestSmsSender implements SmsSender
{
    private static Logger _logger = LoggerFactory.getLogger(RestSmsSender.class);
    private final ExceptionFactory _exceptionFactory;
    private final WebServiceClient _webServiceClient;
    private final Json _json;

    private static final String INVALID_NUMBER_ERROR = "invalid-phonenumber";

    private static final String unexpectedErrorMessage = "An unexpected error has occurred";

    public RestSmsSender(SmsRestPluginConfiguration configuration)
    {
        _exceptionFactory = configuration.getExceptionFactory();
        _webServiceClient = configuration.getWebServiceClient();
        _json = configuration.getJson();
    }

    @Override
    public boolean sendSms(String toNumber, String msg)
    {
        _logger.trace("Sending SMS to number = {} using REST", toNumber);

        Map<String, String> message = new HashMap<>();
        message.put("to", toNumber);
        message.put("message", msg);

        return executeRequest(message);
    }

    private boolean executeRequest(Map<String, String> message)
    {
        String requestBody = _json.toJson(message);

        try
        {
            HttpResponse response = _webServiceClient.request()
                    .accept(ContentType.JSON.getContentType())
                    .contentType(ContentType.JSON.getContentType())
                    .body(HttpRequest.fromString(requestBody, StandardCharsets.UTF_8))
                    .post()
                    .response();

            int httpStatusCode = response.statusCode();

            if (httpStatusCode == 200)
            {
                return true;
            }

            String errorString = parseError(response);

            if (httpStatusCode == 400 && INVALID_NUMBER_ERROR.equals(errorString))
            {
                _logger.debug("Invalid phone number when attempting to send sms");
                return false;
            }

            _logger.warn("Failed to send SMS through REST backend. {}", errorString);
            throw _exceptionFactory.internalServerException(ErrorCode.EXTERNAL_SERVICE_ERROR);

        }
        catch (HttpClientException e)
        {
            _logger.warn("Error when communicating with SMS REST backend {}", e.getMessage());
            throw _exceptionFactory.internalServerException(
                    ErrorCode.EXTERNAL_SERVICE_ERROR,
                    unexpectedErrorMessage);

        }
    }

    private String parseError(HttpResponse response)
    {
        try
        {
            String responseBody = response.body(HttpResponse.asString());

            Map<String, Object> responseData = _json.fromJson(responseBody);

            if (responseData.containsKey("error"))
            {
                return responseData.get("error").toString();
            }
        }
        catch (Json.JsonException jse)
        {
            _logger.warn("Invalid syntax in error response from SMS rest backend");
        }

        return "";
    }

}
