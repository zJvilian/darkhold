package com.quiz.darkhold.user.controller;

import com.quiz.darkhold.init.FileUploadUtil;
import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import com.quiz.darkhold.user.entity.Role;
import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.exception.UserNotFoundException;
import com.quiz.darkhold.user.service.UserService;
import com.quiz.darkhold.user.service.UserServiceImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

@Controller
public class UserController {
    private static final int MIN_NAME = 2;
    private static final int MAX_NAME = 64;
    private static final int MAX_EMAIL = 128;
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 72;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");

    private final Logger logger = LogManager.getLogger(UserController.class);
    private final UserService userService;
    private final MessageSource messages;

    public UserController(final UserService userService, final MessageSource messages) {
        this.userService = userService;
        this.messages = messages;
    }

    /**
     * Show the first page of user management, optionally filtered by search query.
     *
     * @return user management view
     */
    @GetMapping("/userManagement")
    public String manageUsers(@RequestParam(value = "q", required = false) final String query, final Model model) {
        return listByPage(1, query, model);
    }

    @RequestMapping("/user/page/{pageNumber}")
    public String listByPage(@PathVariable final int pageNumber,
                             @RequestParam(value = "q", required = false) final String query,
                             final Model model) {
        logger.info("ListByPage method of user controller ");
        String trimmed = query == null ? "" : query.trim();
        Page<User> page = trimmed.isEmpty()
                ? userService.getAllUsers(pageNumber)
                : userService.searchUsers(pageNumber, trimmed);
        int startCount = page.getTotalElements() == 0 ? 0 : (pageNumber - 1) * UserServiceImpl.USERS_PER_PAGE + 1;
        long endCount = page.getTotalElements() == 0 ? 0 : (long) startCount + UserServiceImpl.USERS_PER_PAGE - 1;
        if (endCount > page.getTotalElements()) {
            endCount = page.getTotalElements();
        }
        model.addAttribute("currentPage", pageNumber);
        model.addAttribute("totalPages", page.getTotalPages());
        model.addAttribute("startCount", startCount);
        model.addAttribute("endCount", endCount);
        model.addAttribute("totalItems", page.getTotalElements());
        model.addAttribute("listusers", page.getContent());
        model.addAttribute("query", trimmed);
        return "user/usermanagement";
    }

    @GetMapping("/user/create")
    public String createUser(final Model model) {
        logger.info("Into the createUser method");
        List<Role> roles = userService.listRoles();
        User user = new User();
        user.setEnabled(true);
        model.addAttribute("userForm", user);
        model.addAttribute("listRoles", roles);
        model.addAttribute("pageTitle", "Create New User");
        return "user/user_form";
    }

    /** Only the fields of user_form.html may be bound; the rest of the entity must not be settable. */
    @InitBinder("user")
    public void allowOnlyFormFields(final WebDataBinder binder) {
        binder.setAllowedFields("id", "firstName", "lastName", "email", "password", "roles", "enabled", "photo");
    }

    @PostMapping("/users/save")
    public String saveUser(final User user, final RedirectAttributes redirectAttributes,
                           @RequestParam("image") final MultipartFile multipartFile,
                           @AuthenticationPrincipal final DarkholdUserDetails principal,
                           final Model model) throws IOException {
        logger.info("Into the saveUser method, user id is {}", user.getId());
        String validationError = validateUserForm(user, principal);
        if (validationError != null) {
            return returnUserFormWithError(user, model, validationError);
        }
        boolean hasUpload = multipartFile != null && !multipartFile.isEmpty();
        String fileName = null;
        if (hasUpload) {
            try {
                fileName = FileUploadUtil.imageFileName(multipartFile.getOriginalFilename());
            } catch (IllegalArgumentException ex) {
                redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
                return "redirect:/userManagement";
            }
            if (FileUploadUtil.detectImageExtension(multipartFile) == null) {
                return returnUserFormWithError(user, model, msg("account.error.photo",
                        "Profile picture must be a valid PNG, JPEG, GIF or WebP image."));
            }
            user.setPhoto(fileName);
        } else {
            // the hidden photo field is client-controlled: keep it to a plain file name
            user.setPhoto(StringUtils.hasText(user.getPhoto()) ? FileUploadUtil.baseName(user.getPhoto()) : null);
        }
        if (Boolean.FALSE.equals(userService.isEmailUnique(user.getId(), user.getEmail()))) {
            return returnUserFormWithError(user, model, msg("account.error.emailTaken",
                    "This email address is already in use."));
        }
        User savedUser;
        try {
            savedUser = userService.save(user);
        } catch (DataIntegrityViolationException ex) {
            if (Boolean.FALSE.equals(userService.isEmailUnique(user.getId(), user.getEmail()))) {
                return returnUserFormWithError(user, model, msg("account.error.emailTaken",
                        "This email address is already in use."));
            }
            throw ex;
        }
        if (hasUpload) {
            String uploadDir = "user-photos/" + savedUser.getId();
            FileUploadUtil.cleanDir(uploadDir);
            FileUploadUtil.saveFile(uploadDir, fileName, multipartFile);
        }
        redirectAttributes.addFlashAttribute("message", "The user has been saved successfully");
        return "redirect:/userManagement";
    }

    private String validateUserForm(final User user, final DarkholdUserDetails principal) {
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String email = user.getEmail() == null ? "" : user.getEmail().trim();
        user.setFirstName(first);
        user.setLastName(last);
        user.setEmail(email);
        if (first.length() < MIN_NAME || first.length() > MAX_NAME
                || last.length() < MIN_NAME || last.length() > MAX_NAME) {
            return msg("account.error.name", "First and last name must be between 2 and 64 characters.");
        }
        if (email.length() > MAX_EMAIL || !EMAIL_PATTERN.matcher(email).matches()) {
            return msg("account.error.email", "Please enter a valid email address (max 128 characters).");
        }
        String password = user.getPassword() == null ? "" : user.getPassword();
        if (user.getId() == null) {
            if (password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
                return msg("user.error.passwordLength", "Password must be between 8 and 72 characters.");
            }
        } else if (!password.isEmpty() && (password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD)) {
            return msg("user.error.passwordLength", "Password must be between 8 and 72 characters.");
        }
        if (user.getId() != null && !Boolean.TRUE.equals(user.getEnabled()) && isSelf(user.getId(), principal)) {
            return msg("user.cannotDisableSelf", "You cannot disable your own account.");
        }
        return null;
    }

    private String returnUserFormWithError(final User user, final Model model, final String errorMessage) {
        user.setPassword("");
        model.addAttribute("userForm", user);
        model.addAttribute("listRoles", userService.listRoles());
        model.addAttribute("pageTitle",
                user.getId() == null ? "Create New User" : "Edit User (ID: " + user.getId() + ")");
        model.addAttribute("errorMessage", errorMessage);
        return "user/user_form";
    }

    @GetMapping("/user/edit/{id}")
    public String editUser(@PathVariable final Long id, final Model model,
                           final RedirectAttributes redirectAttributes) {
        logger.info("Into the editUser method, id is {}", id);
        try {
            User user = userService.get(id);
            model.addAttribute("userForm", user);
            model.addAttribute("pageTitle", "Edit User (ID: " + id + ")");
            model.addAttribute("listRoles", userService.listRoles());
            return "user/user_form";
        } catch (UserNotFoundException ex) {
            redirectAttributes.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/userManagement";
    }

    @PostMapping("/user/delete/{id}")
    public String deleteUser(@PathVariable final Long id,
                             @AuthenticationPrincipal final DarkholdUserDetails principal,
                             final RedirectAttributes redirectAttributes) {
        logger.info("Into the deleteUser method, id is {}", id);
        if (isSelf(id, principal)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    msg("user.cannotDeleteSelf", "You cannot delete your own account."));
            return "redirect:/userManagement";
        }
        try {
            userService.delete(id);
            redirectAttributes.addFlashAttribute("message", "The user ID " + id + " has been deleted successfully");
        } catch (UserNotFoundException ex) {
            redirectAttributes.addFlashAttribute("message", ex.getMessage());
        } catch (DataIntegrityViolationException ex) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    messages.getMessage("user.deleteInUse", null, LocaleContextHolder.getLocale()));
        }
        return "redirect:/userManagement";
    }

    @PostMapping("/users/{id}/enabled/{status}")
    public String updateUserEnabledStatus(@PathVariable final Long id,
                                          @PathVariable("status") final boolean enabled,
                                          @AuthenticationPrincipal final DarkholdUserDetails principal,
                                          final RedirectAttributes redirectAttributes) {
        logger.info("Into the updateUserEnabledStatus method, id is {}, status is {}", id, enabled);
        if (!enabled && isSelf(id, principal)) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    msg("user.cannotDisableSelf", "You cannot disable your own account."));
            return "redirect:/userManagement";
        }
        userService.updateUserEnabledStatus(id, enabled);
        String status = enabled ? "enabled" : "disabled";
        String message = "The user ID " + id + " has been " + status + " successfully";
        redirectAttributes.addFlashAttribute("message", message);
        return "redirect:/userManagement";
    }

    private boolean isSelf(final Long id, final DarkholdUserDetails principal) {
        return principal != null && principal.getUser() != null && id.equals(principal.getUser().getId());
    }

    private String msg(final String code, final String fallback) {
        String resolved = messages.getMessage(code, null, fallback, LocaleContextHolder.getLocale());
        return resolved != null ? resolved : fallback;
    }
}

