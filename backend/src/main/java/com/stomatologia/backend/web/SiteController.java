package com.stomatologia.backend.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Сайт онлайн-записи — одностраничное приложение: страницы, открытые по прямой ссылке, отдают index.html,
 * а нужный экран рисует React Router в браузере.
 */
@Controller
public class SiteController {

    @GetMapping({"/services", "/doctors", "/booking", "/booking/{token}", "/privacy"})
    public String page() {
        return "forward:/index.html";
    }
}
