package com.quiz.darkhold.legal;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LegalController {

    @GetMapping("/impressum")
    public String imprint() {
        return "legal/impressum";
    }

    @GetMapping("/datenschutz")
    public String privacy() {
        return "legal/datenschutz";
    }
}
