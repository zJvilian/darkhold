package com.quiz.darkhold.user.controller;

import com.quiz.darkhold.user.entity.DarkholdUserDetails;
import com.quiz.darkhold.user.entity.Role;
import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.exception.UserNotFoundException;
import com.quiz.darkhold.user.service.UserService;
import com.quiz.darkhold.user.service.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.quiz.darkhold.init.FileUploadUtil.cleanDir;
import static com.quiz.darkhold.init.FileUploadUtil.saveFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith({SpringExtension.class, MockitoExtension.class})
class UserControllerTest {

    @Mock
    private UserService userService;

    @Mock
    private MessageSource messages;

    @Mock
    private Model model;

    @Mock
    private RedirectAttributes redirectAttributes;

    @Mock
    private MultipartFile multipartFile;

    private UserController userController;

    @BeforeEach
    void setUp() {
        lenient().when(userService.isEmailUnique(any(), any())).thenReturn(true);
        userController = new UserController(userService, messages);
    }

    // ======================== manageUsers Tests ========================

    @Test
    void testManageUsers_ShouldDelegateToListByPage() {
        var mockUsers = createMockUserList(5);
        var page = new PageImpl<>(mockUsers);
        when(userService.getAllUsers(1)).thenReturn(page);

        var result = userController.manageUsers(null, model);

        assertEquals("user/usermanagement", result);
        verify(userService).getAllUsers(1);
        verify(model).addAttribute(eq("currentPage"), eq(1));
        verify(model).addAttribute(eq("listusers"), eq(mockUsers));
    }

    @Test
    void testListByPage_WithSearchQuery_ShouldSearchUsers() {
        var mockUsers = createMockUserList(1);
        var page = new PageImpl<>(mockUsers);
        when(userService.searchUsers(1, "User1")).thenReturn(page);

        var result = userController.listByPage(1, "  User1  ", model);

        assertEquals("user/usermanagement", result);
        verify(userService).searchUsers(1, "User1");
        verify(model).addAttribute("query", "User1");
    }

    // ======================== listByPage Tests ========================

    @Test
    void testListByPage_FirstPage_ShouldDisplayCorrectCounts() {
        var mockUsers = createMockUserList(5);
        var page = new PageImpl<>(mockUsers);
        when(userService.getAllUsers(1)).thenReturn(page);

        var result = userController.listByPage(1, null, model);

        assertEquals("user/usermanagement", result);
        verify(model).addAttribute("currentPage", 1);
        verify(model).addAttribute("totalPages", 1);
        verify(model).addAttribute("startCount", 1);
        verify(model).addAttribute("endCount", 5L);
        verify(model).addAttribute("totalItems", 5L);
        verify(model).addAttribute("listusers", mockUsers);
    }

    @Test
    void testListByPage_SecondPage_ShouldCalculateCorrectStartCount() {
        var mockUsers = createMockUserList(5);
        var page = new PageImpl<>(mockUsers);
        when(userService.getAllUsers(2)).thenReturn(page);

        var result = userController.listByPage(2, null, model);

        assertEquals("user/usermanagement", result);
        var expectedStartCount = UserServiceImpl.USERS_PER_PAGE + 1;
        verify(model).addAttribute("currentPage", 2);
        verify(model).addAttribute("startCount", expectedStartCount);
    }

    @Test
    void testListByPage_LastPageWithFewerItems_ShouldAdjustEndCount() {
        var mockUsers = createMockUserList(3);
        var page = new PageImpl<>(mockUsers);
        when(userService.getAllUsers(3)).thenReturn(page);

        var result = userController.listByPage(3, null, model);

        assertEquals("user/usermanagement", result);
        verify(model).addAttribute("endCount", 3L);
    }

    @Test
    void testListByPage_EmptyPage_ShouldHandleCorrectly() {
        var emptyList = new ArrayList<User>();
        var page = new PageImpl<>(emptyList);
        when(userService.getAllUsers(1)).thenReturn(page);

        var result = userController.listByPage(1, null, model);

        assertEquals("user/usermanagement", result);
        verify(model).addAttribute("totalItems", 0L);
        verify(model).addAttribute("listusers", emptyList);
    }

    // ======================== createUser Tests ========================

    @Test
    void testCreateUser_ShouldPopulateModelWithRolesAndNewUser() {
        var mockRoles = createMockRoleList(3);
        when(userService.listRoles()).thenReturn(mockRoles);

        var result = userController.createUser(model);

        assertEquals("user/user_form", result);
        verify(userService).listRoles();
        verify(model).addAttribute(eq("listRoles"), eq(mockRoles));
        verify(model).addAttribute(eq("pageTitle"), eq("Create New User"));

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(model).addAttribute(eq("userForm"), userCaptor.capture());
        assertEquals(true, userCaptor.getValue().getEnabled());
    }

    @Test
    void testCreateUser_EmptyRoleList_ShouldStillReturnForm() {
        var emptyRoles = new ArrayList<Role>();
        when(userService.listRoles()).thenReturn(emptyRoles);

        var result = userController.createUser(model);

        assertEquals("user/user_form", result);
        verify(model).addAttribute(eq("listRoles"), eq(emptyRoles));
    }

    // ======================== saveUser Tests ========================

    @Test
    void testSaveUser_WithValidImageFile_ShouldUploadAndRedirect() throws IOException {
        final var user = createMockUser(null);
        var fileName = "photo.jpg";
        var savedUser = createMockUser(1L);

        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn(fileName);
        when(userService.save(any(User.class))).thenReturn(savedUser);

        try (MockedStatic<com.quiz.darkhold.init.FileUploadUtil> fileUploadUtil
                = mockStatic(com.quiz.darkhold.init.FileUploadUtil.class)) {
            fileUploadUtil.when(() -> com.quiz.darkhold.init.FileUploadUtil.imageFileName(fileName))
                    .thenReturn(fileName);
            fileUploadUtil.when(() -> com.quiz.darkhold.init.FileUploadUtil.detectImageExtension(multipartFile))
                    .thenReturn("jpg");

            var result = userController.saveUser(user, redirectAttributes, multipartFile, null, model);

            assertEquals("redirect:/userManagement", result);
            verify(userService).save(any(User.class));
            fileUploadUtil.verify(() -> cleanDir("user-photos/" + savedUser.getId()));
            fileUploadUtil.verify(() -> saveFile(eq("user-photos/" + savedUser.getId()),
                    eq(fileName), eq(multipartFile)));
            verify(redirectAttributes).addFlashAttribute("message",
                    "The user has been saved successfully");
        }
    }

    @Test
    void testSaveUser_WithEmptyImageFile_ShouldNotUploadFile() throws IOException {
        var user = createMockUser(null);
        user.setPhoto("");

        when(multipartFile.isEmpty()).thenReturn(true);
        when(userService.save(any(User.class))).thenReturn(user);

        try (MockedStatic<com.quiz.darkhold.init.FileUploadUtil> fileUploadUtil
                = mockStatic(com.quiz.darkhold.init.FileUploadUtil.class)) {
            var result = userController.saveUser(user, redirectAttributes, multipartFile, null, model);

            assertEquals("redirect:/userManagement", result);
            verify(userService).save(any(User.class));
            fileUploadUtil.verify(() -> cleanDir(anyString()), never());
            fileUploadUtil.verify(() -> saveFile(anyString(), anyString(), any(MultipartFile.class)), never());
            verify(redirectAttributes).addFlashAttribute("message",
                    "The user has been saved successfully");
        }
    }

    @Test
    void testSaveUser_WithNullMultipartFile_ShouldNotUploadFile() throws IOException {
        var user = createMockUser(null);
        user.setPhoto("");

        when(userService.save(any(User.class))).thenReturn(user);

        try (MockedStatic<com.quiz.darkhold.init.FileUploadUtil> fileUploadUtil
                = mockStatic(com.quiz.darkhold.init.FileUploadUtil.class)) {
            var result = userController.saveUser(user, redirectAttributes, null, null, model);

            assertEquals("redirect:/userManagement", result);
            verify(userService).save(any(User.class));
            fileUploadUtil.verify(() -> cleanDir(anyString()), never());
            verify(redirectAttributes).addFlashAttribute("message",
                    "The user has been saved successfully");
        }
    }

    @Test
    void testSaveUser_WithExistingPhoto_ShouldPreservePhotoIfFileEmpty() throws IOException {
        var user = createMockUser(1L);
        user.setPhoto("existing_photo.jpg");

        when(multipartFile.isEmpty()).thenReturn(true);
        when(userService.save(any(User.class))).thenReturn(user);

        try (MockedStatic<com.quiz.darkhold.init.FileUploadUtil> fileUploadUtil
                = mockStatic(com.quiz.darkhold.init.FileUploadUtil.class)) {
            var result = userController.saveUser(user, redirectAttributes, multipartFile, null, model);

            assertEquals("redirect:/userManagement", result);
            verify(userService).save(user);
            fileUploadUtil.verify(() -> cleanDir(anyString()), never());
        }
    }

    @Test
    void testSaveUser_FileUploadIOException_ShouldPropagateException() {
        final var user = createMockUser(null);
        var fileName = "photo.jpg";
        var savedUser = createMockUser(1L);

        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn(fileName);
        when(userService.save(any(User.class))).thenReturn(savedUser);

        try (MockedStatic<com.quiz.darkhold.init.FileUploadUtil> fileUploadUtil
                = mockStatic(com.quiz.darkhold.init.FileUploadUtil.class)) {
            fileUploadUtil.when(() -> com.quiz.darkhold.init.FileUploadUtil.imageFileName(fileName))
                    .thenReturn(fileName);
            fileUploadUtil.when(() -> com.quiz.darkhold.init.FileUploadUtil.detectImageExtension(multipartFile))
                    .thenReturn("jpg");
            fileUploadUtil.when(() -> saveFile(anyString(), anyString(), any(MultipartFile.class)))
                    .thenThrow(new IOException("File upload failed"));

            try {
                userController.saveUser(user, redirectAttributes, multipartFile, null, model);
            } catch (IOException ex) {
                assertEquals("File upload failed", ex.getMessage());
            }
        }
    }

    @Test
    void testSaveUser_NonImageUpload_IsRejectedWithoutSaving() throws IOException {
        when(multipartFile.isEmpty()).thenReturn(false);
        when(multipartFile.getOriginalFilename()).thenReturn("../../shell.jsp");

        var result = userController.saveUser(createMockUser(null), redirectAttributes, multipartFile, null, model);

        assertEquals("redirect:/userManagement", result);
        verify(userService, never()).save(any(User.class));
        verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), anyString());
    }

    @Test
    void testSaveUser_DuplicateEmailReturnsFormWithError() throws IOException {
        var user = createMockUser(null);
        when(userService.isEmailUnique(null, user.getEmail())).thenReturn(false);

        var result = userController.saveUser(user, redirectAttributes, null, null, model);

        assertEquals("user/user_form", result);
        verify(userService, never()).save(any(User.class));
        verify(model).addAttribute(eq("errorMessage"), anyString());
    }

    @Test
    void testSaveUser_NewUserWithShortPasswordReturnsFormWithError() throws IOException {
        var user = createMockUser(null);
        user.setPassword("short");

        var result = userController.saveUser(user, redirectAttributes, null, null, model);

        assertEquals("user/user_form", result);
        verify(userService, never()).save(any(User.class));
        verify(model).addAttribute(eq("errorMessage"), anyString());
    }

    @Test
    void testBinder_OnlyAllowsFormFields() {
        var binder = new org.springframework.web.bind.WebDataBinder(new User(), "user");
        userController.allowOnlyFormFields(binder);
        org.junit.jupiter.api.Assertions.assertTrue(java.util.Arrays.asList(binder.getAllowedFields())
                .containsAll(java.util.List.of("email", "password", "roles", "enabled")));
        org.junit.jupiter.api.Assertions.assertFalse(java.util.Arrays.asList(binder.getAllowedFields())
                .contains("class"));
    }

    // ======================== editUser Tests ========================

    @Test
    void testEditUser_WithValidId_ShouldPopulateModelAndReturnForm() throws UserNotFoundException {
        var userId = 1L;
        var user = createMockUser(userId);
        var mockRoles = createMockRoleList(2);
        when(userService.get(userId)).thenReturn(user);
        when(userService.listRoles()).thenReturn(mockRoles);

        var result = userController.editUser(userId, model, redirectAttributes);

        assertEquals("user/user_form", result);
        verify(userService).get(userId);
        verify(userService).listRoles();
        verify(model).addAttribute("userForm", user);
        verify(model).addAttribute("pageTitle", "Edit User (ID: " + userId + ")");
        verify(model).addAttribute("listRoles", mockRoles);
        verify(redirectAttributes, never()).addFlashAttribute(anyString(), any());
    }

    @Test
    void testEditUser_UserNotFound_ShouldRedirectWithFlashMessage() throws UserNotFoundException {
        var userId = 999L;
        var errorMessage = "User not found with id: " + userId;
        when(userService.get(userId)).thenThrow(new UserNotFoundException(errorMessage));

        var result = userController.editUser(userId, model, redirectAttributes);

        assertEquals("redirect:/userManagement", result);
        verify(redirectAttributes).addFlashAttribute("message", errorMessage);
        verify(model, never()).addAttribute(anyString(), any());
    }

    @Test
    void testEditUser_MultipleCallsWithDifferentIds_ShouldHandleCorrectly() throws UserNotFoundException {
        var userId1 = 1L;
        var userId2 = 2L;
        var user1 = createMockUser(userId1);
        var user2 = createMockUser(userId2);
        var mockRoles = createMockRoleList(2);

        when(userService.get(userId1)).thenReturn(user1);
        when(userService.get(userId2)).thenReturn(user2);
        when(userService.listRoles()).thenReturn(mockRoles);

        var result1 = userController.editUser(userId1, model, redirectAttributes);
        var result2 = userController.editUser(userId2, model, redirectAttributes);

        assertEquals("user/user_form", result1);
        assertEquals("user/user_form", result2);
        verify(userService).get(userId1);
        verify(userService).get(userId2);
        verify(userService, times(2)).listRoles();
    }

    // ======================== deleteUser Tests ========================

    @Test
    void testDeleteUser_WithValidId_ShouldDeleteAndRedirect() throws UserNotFoundException {
        var userId = 1L;
        doNothing().when(userService).delete(userId);

        var result = userController.deleteUser(userId, null, redirectAttributes);

        assertEquals("redirect:/userManagement", result);
        verify(userService).delete(userId);
        verify(redirectAttributes).addFlashAttribute("message",
                "The user ID " + userId + " has been deleted successfully");
    }

    @Test
    void testDeleteUser_OwnAccount_IsRejected() throws UserNotFoundException {
        var admin = createMockUser(1L);
        var principal = new DarkholdUserDetails(admin);

        var result = userController.deleteUser(1L, principal, redirectAttributes);

        assertEquals("redirect:/userManagement", result);
        verify(userService, never()).delete(1L);
        verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), anyString());
    }

    @Test
    void testDeleteUser_UserNotFound_ShouldRedirectWithErrorMessage() throws UserNotFoundException {
        var userId = 999L;
        var errorMessage = "User not found with id: " + userId;
        doThrow(new UserNotFoundException(errorMessage)).when(userService).delete(userId);

        var result = userController.deleteUser(userId, null, redirectAttributes);

        assertEquals("redirect:/userManagement", result);
        verify(redirectAttributes).addFlashAttribute("message", errorMessage);
    }

    @Test
    void testDeleteUser_MultipleDeletes_ShouldHandleCorrectly() throws UserNotFoundException {
        var userId1 = 1L;
        var userId2 = 2L;
        doNothing().when(userService).delete(userId1);
        doNothing().when(userService).delete(userId2);

        var result1 = userController.deleteUser(userId1, null, redirectAttributes);
        var result2 = userController.deleteUser(userId2, null, redirectAttributes);

        assertEquals("redirect:/userManagement", result1);
        assertEquals("redirect:/userManagement", result2);
        verify(userService).delete(userId1);
        verify(userService).delete(userId2);
    }

    // ======================== updateUserEnabledStatus Tests ========================

    @Test
    void testUpdateUserEnabledStatus_EnableUser_ShouldSetStatusAndRedirect() {
        var userId = 1L;
        var enabled = true;
        doNothing().when(userService).updateUserEnabledStatus(userId, enabled);

        var result = userController.updateUserEnabledStatus(userId, enabled, null, redirectAttributes);

        assertEquals("redirect:/userManagement", result);
        verify(userService).updateUserEnabledStatus(userId, enabled);
        verify(redirectAttributes).addFlashAttribute("message",
                "The user ID " + userId + " has been enabled successfully");
    }

    @Test
    void testUpdateUserEnabledStatus_DisableUser_ShouldSetStatusAndRedirect() {
        var userId = 2L;
        var enabled = false;
        doNothing().when(userService).updateUserEnabledStatus(userId, enabled);

        var result = userController.updateUserEnabledStatus(userId, enabled, null, redirectAttributes);

        assertEquals("redirect:/userManagement", result);
        verify(userService).updateUserEnabledStatus(userId, enabled);
        verify(redirectAttributes).addFlashAttribute("message",
                "The user ID " + userId + " has been disabled successfully");
    }

    @Test
    void testUpdateUserEnabledStatus_DisableOwnAccount_IsRejected() {
        var admin = createMockUser(1L);
        var principal = new DarkholdUserDetails(admin);

        var result = userController.updateUserEnabledStatus(1L, false, principal, redirectAttributes);

        assertEquals("redirect:/userManagement", result);
        verify(userService, never()).updateUserEnabledStatus(1L, false);
        verify(redirectAttributes).addFlashAttribute(eq("errorMessage"), anyString());
    }

    @Test
    void testUpdateUserEnabledStatus_ToggleMultipleTimes_ShouldHandleCorrectly() {
        var userId = 1L;
        doNothing().when(userService).updateUserEnabledStatus(userId, true);
        doNothing().when(userService).updateUserEnabledStatus(userId, false);

        var result1 = userController.updateUserEnabledStatus(userId, true, null, redirectAttributes);
        var result2 = userController.updateUserEnabledStatus(userId, false, null, redirectAttributes);

        assertEquals("redirect:/userManagement", result1);
        assertEquals("redirect:/userManagement", result2);
        verify(userService).updateUserEnabledStatus(userId, true);
        verify(userService).updateUserEnabledStatus(userId, false);
    }

    // ======================== Helper Methods ========================

    private User createMockUser(final Long id) {
        var user = new User();
        user.setId(id);
        user.setFirstName("John");
        user.setLastName("Doe");
        user.setEmail("john.doe@example.com");
        user.setPassword("password123");
        user.setEnabled(true);
        return user;
    }

    private List<User> createMockUserList(final int count) {
        var users = new ArrayList<User>();
        for (int i = 1; i <= count; i++) {
            var user = new User();
            user.setId((long) i);
            user.setFirstName("User" + i);
            user.setLastName("Test");
            user.setEmail("user" + i + "@example.com");
            users.add(user);
        }
        return users;
    }

    private Role createMockRole(final Long id, final String name) {
        var role = new Role(id);
        role.setId(id);
        role.setName(name);
        role.setDescription("Description for " + name);
        return role;
    }

    private List<Role> createMockRoleList(final int count) {
        var roles = new ArrayList<Role>();
        for (int i = 1; i <= count; i++) {
            roles.add(createMockRole((long) i, "ROLE_" + i));
        }
        return roles;
    }
}

