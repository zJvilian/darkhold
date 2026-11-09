package com.quiz.darkhold.account.controller;

import com.quiz.darkhold.init.FileUploadUtil;
import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.exception.UserNotFoundException;
import com.quiz.darkhold.user.service.UserService;
import com.quiz.darkhold.user.validator.UserValidator;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Controller for user account settings and preferences. Users can only ever edit themselves.
 */
@Controller
@RequestMapping("/account")
@PreAuthorize("isAuthenticated()")
public class AccountController {
    private static final int MAX_NAME = 64;
    private static final int MAX_EMAIL = 128;
    private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
    private static final Map<String, String> PHOTO_TYPES =
            Map.of("image/png", "png", "image/jpeg", "jpg", "image/gif", "gif");

    private final UserService userService;
    private final UserValidator userValidator;
    private final FindByIndexNameSessionRepository<?> sessions;

    public AccountController(final UserService userService, final UserValidator userValidator,
                             final FindByIndexNameSessionRepository<?> sessions) {
        this.userService = userService;
        this.userValidator = userValidator;
        this.sessions = sessions;
    }

    /** The profile form; every text is trimmed, a missing one becomes empty. */
    public record ProfileForm(String firstName, String lastName, String email, String currentPassword) {
        public ProfileForm {
            firstName = firstName == null ? "" : firstName.trim();
            lastName = lastName == null ? "" : lastName.trim();
            email = email == null ? "" : email.trim();
            currentPassword = currentPassword == null ? "" : currentPassword;
        }
    }

    /**
     * Show account settings page.
     *
     * @return view name
     */
    @GetMapping({"", "/"})
    public String showAccountSettings() {
        return "account/settings";
    }

    @PostMapping("/profile")
    public String updateProfile(@AuthenticationPrincipal final DarkholdUserDetails principal,
                                final ProfileForm form, @RequestParam("image") final MultipartFile image,
                                final HttpSession session, final RedirectAttributes redirect)
            throws IOException, UserNotFoundException {
        User me = principal.getUser();
        String extension = image.isEmpty() ? null : PHOTO_TYPES.get(image.getContentType());
        String error = profileError(me, form, !image.isEmpty() && extension == null);
        if (error != null) {
            return fail(redirect, error);
        }
        String photo = photoName(extension);
        userService.updateProfile(me.getId(), form.firstName(), form.lastName(), form.email(), photo);
        savePhoto(me.getId(), photo, image);
        refreshLogin(me.getId(), session);
        redirect.addFlashAttribute("message", "account.profileUpdated");
        return "redirect:/account";
    }

    // Server-chosen name: no client path reaches the disk, and a new name defeats browser caching.
    private String photoName(final String extension) {
        return extension == null ? null : "photo_" + System.currentTimeMillis() + "." + extension;
    }

    private void savePhoto(final Long id, final String photo, final MultipartFile image) throws IOException {
        if (photo != null) {
            String dir = "user-photos/" + id;
            FileUploadUtil.cleanDir(dir);
            FileUploadUtil.saveFile(dir, photo, image);
        }
    }

    /** The stored session holds its own copy of the user; replace it so the navbar shows the new values. */
    private void refreshLogin(final Long id, final HttpSession session) throws UserNotFoundException {
        var fresh = new DarkholdUserDetails(userService.get(id));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                fresh, null, fresh.getAuthorities()));
        SecurityContextHolder.setContext(context);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
    }

    /**
     * Validate the profile form.
     *
     * @return the message key of the first problem, or null when the input is fine
     */
    private String profileError(final User me, final ProfileForm form, final boolean badPhoto)
            throws UserNotFoundException {
        if (form.firstName().isEmpty() || form.lastName().isEmpty()
                || form.firstName().length() > MAX_NAME || form.lastName().length() > MAX_NAME) {
            return "account.error.name";
        } else if (badPhoto) {
            return "account.error.photo";
        } else if (form.email().length() > MAX_EMAIL || !EMAIL.matcher(form.email()).matches()) {
            return "account.error.email";
        } else if (!userService.isEmailUnique(me.getId(), form.email())) {
            return "account.error.emailTaken";
        }
        // The email is the login name, so changing it needs the password like a password change does.
        boolean changed = !form.email().equals(userService.get(me.getId()).getEmail());
        return changed && !userService.passwordMatches(me.getId(), form.currentPassword())
                ? "account.error.current" : null;
    }

    @PostMapping("/password")
    public String changePassword(@AuthenticationPrincipal final DarkholdUserDetails principal,
                                 @RequestParam final String currentPassword,
                                 @RequestParam final String newPassword,
                                 @RequestParam final String confirmPassword,
                                 final HttpSession session,
                                 final RedirectAttributes redirect) throws UserNotFoundException {
        if (!newPassword.equals(confirmPassword)) {
            return fail(redirect, "account.error.mismatch");
        }
        if (!isStrong(newPassword)) {
            return fail(redirect, "account.error.weak");
        }
        if (!userService.changePassword(principal.getUser().getId(), currentPassword, newPassword)) {
            return fail(redirect, "account.error.current");
        }
        expireOtherSessions(principal.getUser().getId(), session);
        redirect.addFlashAttribute("message", "account.passwordChanged");
        return "redirect:/account";
    }

    // Whoever else is signed in with the old password (e.g. a stolen session) is logged out.
    private void expireOtherSessions(final Long userId, final HttpSession current) {
        sessions.findByPrincipalName(String.valueOf(userId)).keySet().stream()
                .filter(id -> !id.equals(current.getId()))
                .forEach(sessions::deleteById);
    }

    private boolean isStrong(final String password) {
        User candidate = new User();
        candidate.setPassword(password);
        var errors = new BeanPropertyBindingResult(candidate, "user");
        userValidator.validatePassword(errors, candidate);
        return !errors.hasErrors();
    }

    private String fail(final RedirectAttributes redirect, final String errorKey) {
        redirect.addFlashAttribute("errorMessage", errorKey);
        return "redirect:/account";
    }
}
