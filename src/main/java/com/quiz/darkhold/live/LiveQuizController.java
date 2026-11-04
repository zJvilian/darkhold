package com.quiz.darkhold.live;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.quiz.darkhold.init.RateLimitingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.Principal;
import java.util.Map;

@Controller
public class LiveQuizController {
    private final LiveQuizService service;
    private final RateLimitingService rateLimits;
    private final String publicBaseUrl;

    public LiveQuizController(final LiveQuizService service, final RateLimitingService rateLimits,
                              @Value("${darkhold.public-base-url:}") final String publicBaseUrl) {
        this.service = service;
        this.rateLimits = rateLimits;
        this.publicBaseUrl = publicBaseUrl;
    }

    @PostMapping("/live/host")
    public String host(@RequestParam("challenge_id") final long challengeId, final Principal principal,
                       final RedirectAttributes flash) {
        try {
            return "redirect:/play/" + service.host(challengeId, username(principal));
        } catch (ResponseStatusException ex) {
            flash.addFlashAttribute("liveError", ex.getReason());
            return "redirect:/edit_challenge/" + challengeId;
        }
    }

    @GetMapping("/join")
    public String joinPage(@RequestParam(defaultValue = "") final String pin, final Model model) {
        model.addAttribute("pin", pin.matches("[0-9]{4,6}") ? pin : "");
        model.addAttribute("avatars", LiveQuizService.AVATARS);
        return "live/join";
    }

    @PostMapping("/join")
    public String join(@RequestParam final String pin, @RequestParam final String name,
                       @RequestParam final String avatar, final HttpServletRequest request,
                       final HttpServletResponse response, final RedirectAttributes flash) {
        String address = request.getRemoteAddr();
        if (rateLimits.isBlocked(address) || !rateLimits.isAllowed(address)) {
            flash.addFlashAttribute("joinError", service.message("live.error.rateLimit"));
            return "redirect:/join";
        }
        try {
            HttpSession session = request.getSession();
            String token = service.join(pin, name, avatar, playerToken(request, pin));
            session.setAttribute("livePlayer:" + pin, token);
            rememberPlayer(response, request.isSecure(), pin, token);
            request.changeSessionId();
            rateLimits.recordSuccessfulAttempt(address);
            return "redirect:/play/" + pin;
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                rateLimits.recordFailedAttempt(address);
            }
            flash.addFlashAttribute("joinError", ex.getReason());
            flash.addFlashAttribute("nickname", name);
            return "redirect:/join?pin=" + (pin.matches("[0-9]{4,6}") ? pin : "");
        }
    }

    @GetMapping("/play/{pin}")
    public String play(@PathVariable final String pin, final HttpSession session, final Principal principal,
                       final HttpServletRequest request, final Model model, final RedirectAttributes flash) {
        try {
            Map<String, Object> state = service.state(pin, username(principal), playerToken(request, pin));
            model.addAttribute("pin", pin);
            model.addAttribute("host", state.get("host"));
            model.addAttribute("joinUrl", joinUrl(request, pin));
            return "live/room";
        } catch (ResponseStatusException ex) {
            flash.addFlashAttribute("joinError", ex.getReason());
            return "redirect:/join";
        }
    }

    @GetMapping("/api/live/{pin}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> state(@PathVariable final String pin,
                                                    final HttpServletRequest request, final Principal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.state(pin, username(principal), playerToken(request, pin)));
    }

    @PostMapping("/api/live/{pin}/answer")
    @ResponseBody
    public Map<String, Object> answer(@PathVariable final String pin, @RequestBody final Answer answer,
                                      final HttpServletRequest request) {
        return service.answer(pin, playerToken(request, pin), answer.questionIndex(), answer.answer());
    }

    @PostMapping("/api/live/{pin}/control")
    @ResponseBody
    public Map<String, Object> control(@PathVariable final String pin, @RequestBody final Control control,
                                       final Principal principal) {
        return service.control(pin, username(principal), control.action(),
                control.questionIndex(), control.playerName());
    }

    @GetMapping(value = "/api/live/{pin}/qr", produces = "image/png")
    @ResponseBody
    public byte[] qr(@PathVariable final String pin, final HttpServletRequest request,
                     final Principal principal, final HttpSession session) throws WriterException, IOException {
        service.state(pin, username(principal), playerToken(request, pin));
        var matrix = new QRCodeWriter().encode(joinUrl(request, pin), BarcodeFormat.QR_CODE, 320, 320);
        BufferedImage image = new BufferedImage(320, 320, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < 320; row++) {
            for (int column = 0; column < 320; column++) {
                image.setRGB(column, row, matrix.get(column, row) ? 0x000000 : 0xFFFFFF);
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", output);
        return output.toByteArray();
    }

    private String joinUrl(final HttpServletRequest request, final String pin) {
        String base = publicBaseUrl.isBlank()
                ? UriComponentsBuilder.fromUriString(request.getRequestURL().toString())
                    .replacePath(request.getContextPath()).replaceQuery(null).build().toUriString()
                : publicBaseUrl;
        return UriComponentsBuilder.fromUriString(base).path("/join").queryParam("pin", pin)
                .build().toUriString();
    }

    private String username(final Principal principal) {
        return LiveQuizService.hostIdentity(principal);
    }

    private String playerToken(final HttpServletRequest request, final String pin) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("livePlayer:" + pin) instanceof String token) {
            return token;
        }
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (cookie.getName().equals("darkhold_player_" + pin)) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private void rememberPlayer(final HttpServletResponse response, final boolean secure,
                                final String pin, final String token) {
        Cookie cookie = new Cookie("darkhold_player_" + pin, token);
        cookie.setHttpOnly(true);
        cookie.setSecure(secure);
        cookie.setAttribute("SameSite", "Lax");
        cookie.setPath("/");
        cookie.setMaxAge(24 * 60 * 60);
        response.addCookie(cookie);
    }

    @ExceptionHandler(ResponseStatusException.class)
    @ResponseBody
    public ResponseEntity<Map<String, String>> gameError(final ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", ex.getReason()));
    }

    public record Answer(int questionIndex, String answer) { }

    public record Control(String action, int questionIndex, String playerName) { }
}
