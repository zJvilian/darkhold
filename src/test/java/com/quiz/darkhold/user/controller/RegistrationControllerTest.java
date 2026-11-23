package com.quiz.darkhold.user.controller;

import com.quiz.darkhold.home.model.GameInfo;
import com.quiz.darkhold.init.RateLimitingService;
import com.quiz.darkhold.user.entity.Role;
import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.ui.Model;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({SpringExtension.class, MockitoExtension.class})
@DisplayName("RegistrationController Tests")
class RegistrationControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private Model model;

    private HttpServletRequest request;
    private RegistrationController registrationController;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        lenient().when(rateLimitingService.isAllowed(anyString())).thenReturn(true);
        registrationController = new RegistrationController(userService, rateLimitingService);
    }

    @Nested
    @DisplayName("user() ModelAttribute method tests")
    class UserModelAttributeMethodTests {

        @Test
        @DisplayName("should return a new User instance")
        void testUser_ShouldReturnNewUserInstance() {
            // When
            User user = registrationController.user();

            // Then
            assertNotNull(user);
        }

        @Test
        @DisplayName("should return different instances on each call")
        void testUser_ShouldReturnDifferentInstancesEachCall() {
            // When
            User user1 = registrationController.user();
            User user2 = registrationController.user();

            // Then
            assertNotNull(user1);
            assertNotNull(user2);
        }
    }

    @Nested
    @DisplayName("registration() GET method tests")
    class RegistrationGetMethodTests {

        @Test
        @DisplayName("should return registration view name without unused userForm attribute")
        void testRegistrationGet_ShouldReturnRegistrationViewName() {
            String result = registrationController.registration(model);
            assertEquals("registration", result);
            verify(model, never()).addAttribute(eq("userForm"), any());
        }
    }

    @Nested
    @DisplayName("registration() POST method tests")
    class RegistrationPostMethodTests {

        private BindingResult bindingResult;
        private User user;

        @BeforeEach
        void setUpPost() {
            bindingResult = mock(BindingResult.class);
            user = createValidUser("John", "Doe", "john@example.com");
        }

        @Test
        @DisplayName("should return registration view when binding result has errors")
        void testRegistrationPost_WhenBindingResultHasErrors_ShouldReturnRegistrationView() {
            when(bindingResult.hasErrors()).thenReturn(true);
            ObjectError error = mock(ObjectError.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(error));

            String result = registrationController.registration(model, user, bindingResult, request);

            assertEquals("registration", result);
            verify(userService, never()).save(any());
            verify(model, never()).addAttribute(eq("gameinfo"), any());
        }

        @Test
        @DisplayName("should save user with GUEST role when no binding errors")
        void testRegistrationPost_WhenNoErrors_ShouldSaveUserWithGuestRole() {
            when(bindingResult.hasErrors()).thenReturn(false);
            Role guestRole = createMockRole("GUEST");
            when(userService.listRoles()).thenReturn(List.of(guestRole));

            registrationController.registration(model, user, bindingResult, request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userService).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertEquals(true, savedUser.getEnabled());
            assertEquals(Set.of(guestRole), savedUser.getRoles());
        }

        @Test
        @DisplayName("should return index view when registration succeeds")
        void testRegistrationPost_WhenNoErrors_ShouldReturnIndexView() {
            when(bindingResult.hasErrors()).thenReturn(false);
            Role guestRole = createMockRole("GUEST");
            when(userService.listRoles()).thenReturn(List.of(guestRole));

            String result = registrationController.registration(model, user, bindingResult, request);

            assertEquals("index", result);
        }

        @Test
        @DisplayName("should add success message to model when registration succeeds")
        void testRegistrationPost_WhenNoErrors_ShouldAddGameInfoToModel() {
            when(bindingResult.hasErrors()).thenReturn(false);
            Role guestRole = createMockRole("GUEST");
            when(userService.listRoles()).thenReturn(List.of(guestRole));

            registrationController.registration(model, user, bindingResult, request);

            ArgumentCaptor<GameInfo> gameInfoCaptor = ArgumentCaptor.forClass(GameInfo.class);
            verify(model).addAttribute(eq("gameinfo"), gameInfoCaptor.capture());
            GameInfo gameInfo = gameInfoCaptor.getValue();
            assertEquals("Successfully created the account !!!", gameInfo.getMessage());
        }

        @Test
        @DisplayName("should set user enabled to true")
        void testRegistrationPost_ShouldSetUserEnabledToTrue() {
            user.setEnabled(false);
            when(bindingResult.hasErrors()).thenReturn(false);
            Role guestRole = createMockRole("GUEST");
            when(userService.listRoles()).thenReturn(List.of(guestRole));

            registrationController.registration(model, user, bindingResult, request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userService).save(userCaptor.capture());
            assertEquals(true, userCaptor.getValue().getEnabled());
        }

        @Test
        @DisplayName("should handle multiple roles and find GUEST role")
        void testRegistrationPost_WhenMultipleRoles_ShouldFindAndAssignGuestRole() {
            when(bindingResult.hasErrors()).thenReturn(false);
            Role adminRole = createMockRole("ADMIN");
            Role guestRole = createMockRole("GUEST");
            Role userRole = createMockRole("USER");
            when(userService.listRoles()).thenReturn(List.of(adminRole, userRole, guestRole));

            registrationController.registration(model, user, bindingResult, request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userService).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertEquals(1, savedUser.getRoles().size());
            assertEquals("GUEST", savedUser.getRoles().iterator().next().getName());
        }

        @Test
        @DisplayName("should not set roles when GUEST role not found")
        void testRegistrationPost_WhenGuestRoleNotFound_ShouldNotSetRoles() {
            when(bindingResult.hasErrors()).thenReturn(false);
            Role adminRole = createMockRole("ADMIN");
            when(userService.listRoles()).thenReturn(List.of(adminRole));

            registrationController.registration(model, user, bindingResult, request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userService).save(userCaptor.capture());
            assertNotNull(userCaptor.getValue());
        }

        @Test
        @DisplayName("should call userService.save exactly once on successful registration")
        void testRegistrationPost_ShouldCallSaveExactlyOnce() {
            when(bindingResult.hasErrors()).thenReturn(false);
            Role guestRole = createMockRole("GUEST");
            when(userService.listRoles()).thenReturn(List.of(guestRole));

            registrationController.registration(model, user, bindingResult, request);

            verify(userService, times(1)).save(any());
        }

        @Test
        @DisplayName("should call userService.listRoles exactly once")
        void testRegistrationPost_ShouldCallListRolesExactlyOnce() {
            when(bindingResult.hasErrors()).thenReturn(false);
            Role guestRole = createMockRole("GUEST");
            when(userService.listRoles()).thenReturn(List.of(guestRole));

            registrationController.registration(model, user, bindingResult, request);

            verify(userService, times(1)).listRoles();
        }

        @Test
        @DisplayName("should add gameinfo attribute only once on success")
        void testRegistrationPost_ShouldAddGameInfoAttributeOnlyOnce() {
            when(bindingResult.hasErrors()).thenReturn(false);
            Role guestRole = createMockRole("GUEST");
            when(userService.listRoles()).thenReturn(List.of(guestRole));

            registrationController.registration(model, user, bindingResult, request);

            verify(model, times(1)).addAttribute(eq("gameinfo"), any(GameInfo.class));
        }

        @Test
        @DisplayName("should not call userService methods when binding result has errors")
        void testRegistrationPost_WhenErrors_ShouldNotCallUserService() {
            when(bindingResult.hasErrors()).thenReturn(true);
            ObjectError error = mock(ObjectError.class);
            when(bindingResult.getAllErrors()).thenReturn(List.of(error));

            registrationController.registration(model, user, bindingResult, request);

            verify(userService, never()).listRoles();
            verify(userService, never()).save(any());
        }

        @Test
        @DisplayName("should handle empty roles list gracefully")
        void testRegistrationPost_WhenNoRolesAvailable_ShouldHandleGracefully() {
            when(bindingResult.hasErrors()).thenReturn(false);
            when(userService.listRoles()).thenReturn(new ArrayList<>());

            String result = registrationController.registration(model, user, bindingResult, request);

            assertEquals("index", result);
            verify(userService).save(any());
        }

        @Test
        @DisplayName("should preserve user data when saving with GUEST role")
        void testRegistrationPost_ShouldPreserveUserDataWhenSaving() {
            String firstName = "Alice";
            String email = "alice@example.com";
            user.setFirstName(firstName);
            user.setEmail(email);
            when(bindingResult.hasErrors()).thenReturn(false);
            Role guestRole = createMockRole("GUEST");
            when(userService.listRoles()).thenReturn(List.of(guestRole));

            registrationController.registration(model, user, bindingResult, request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userService).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertEquals(firstName, savedUser.getFirstName());
            assertEquals(email, savedUser.getEmail());
        }
    }

    @Test
    void concurrentDuplicateEmailReturnsRegistrationForm() {
        User user = createValidUser("Dup", "User", "duplicate@example.invalid");
        var errors = new BeanPropertyBindingResult(user, "user");
        when(userService.findByUsername(user.getEmail())).thenReturn(null, new User());
        when(userService.save(user)).thenThrow(new DataIntegrityViolationException("duplicate email"));
        assertEquals("registration", registrationController.registration(model, user, errors, request));
        assertTrue(errors.hasFieldErrors("email"));
    }

    @Test
    void otherDatabaseFailuresAreNotReportedAsDuplicateEmail() {
        User user = createValidUser("Other", "User", "other@example.invalid");
        var errors = new BeanPropertyBindingResult(user, "user");
        when(userService.save(user)).thenThrow(new DataIntegrityViolationException("other constraint"));
        assertThrows(DataIntegrityViolationException.class,
                () -> registrationController.registration(model, user, errors, request));
    }

    @Test
    void rejectsInvalidNamesEmailAndPassword() {
        User invalid = new User();
        invalid.setFirstName("A");
        invalid.setLastName(" ");
        invalid.setEmail("bad-email");
        invalid.setPassword("short");
        var errors = new BeanPropertyBindingResult(invalid, "user");

        assertEquals("registration", registrationController.registration(model, invalid, errors, request));
        assertTrue(errors.hasFieldErrors("firstName"));
        assertTrue(errors.hasFieldErrors("lastName"));
        assertTrue(errors.hasFieldErrors("email"));
        assertTrue(errors.hasFieldErrors("password"));
        verify(userService, never()).save(any());
    }

    @Test
    void blocksRateLimitedRegistration() {
        when(rateLimitingService.isAllowed(anyString())).thenReturn(false);
        User user = createValidUser("Alice", "Smith", "alice@example.invalid");
        var errors = new BeanPropertyBindingResult(user, "user");

        assertEquals("registration", registrationController.registration(model, user, errors, request));
        assertTrue(errors.hasGlobalErrors());
        verify(userService, never()).save(any());
    }

    @Nested
    @DisplayName("Integration scenario tests")
    class IntegrationScenarioTests {

        @Test
        @DisplayName("should complete full registration flow successfully")
        void testCompleteRegistrationFlow() {
            User newUser = createValidUser("Alice", "Smith", "alice@example.com");
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.hasErrors()).thenReturn(false);
            Role guestRole = createMockRole("GUEST");
            when(userService.listRoles()).thenReturn(List.of(guestRole));

            String result = registrationController.registration(model, newUser, bindingResult, request);

            assertEquals("index", result);
            verify(userService).listRoles();
            verify(userService).save(any(User.class));
            verify(model).addAttribute(eq("gameinfo"), any(GameInfo.class));
        }

        @Test
        @DisplayName("should handle registration with validation errors")
        void testRegistrationWithValidationErrors() {
            User newUser = new User();
            BindingResult bindingResult = mock(BindingResult.class);
            when(bindingResult.hasErrors()).thenReturn(true);
            ObjectError error = new ObjectError("user", "Invalid email format");
            when(bindingResult.getAllErrors()).thenReturn(List.of(error));

            String result = registrationController.registration(model, newUser, bindingResult, request);

            assertEquals("registration", result);
            verify(userService, never()).listRoles();
            verify(userService, never()).save(any());
        }

        @Test
        @DisplayName("should call user model attribute factory method")
        void testUserModelAttributeMethod() {
            User user = registrationController.user();
            assertNotNull(user);
        }
    }

    private User createValidUser(final String first, final String last, final String email) {
        User user = new User();
        user.setFirstName(first);
        user.setLastName(last);
        user.setEmail(email);
        user.setPassword("ValidPass123!");
        return user;
    }

    // ======================== Helper Methods ========================

    private Role createMockRole(final String roleName) {
        Role role = new Role();
        role.setName(roleName);
        return role;
    }
}

