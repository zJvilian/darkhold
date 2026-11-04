package com.quiz.darkhold.init;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** Covers errors raised before a controller, including CSRF and missing resources. */
@Controller
public class ApplicationErrorController implements ErrorController {
    @RequestMapping("/error")
    public Object error(final HttpServletRequest request) {
        Object attribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int code = attribute instanceof Integer status ? status : 500;
        return ErrorRendering.response(request, ResponseEntity.status(HttpStatusCode.valueOf(code))
                .body(new ErrorResponse("HTTP_" + code, "The request could not be completed.")));
    }
}
