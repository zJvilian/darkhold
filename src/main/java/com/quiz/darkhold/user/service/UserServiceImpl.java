package com.quiz.darkhold.user.service;

import com.quiz.darkhold.init.FileUploadUtil;
import com.quiz.darkhold.user.entity.Role;
import com.quiz.darkhold.user.entity.User;
import com.quiz.darkhold.user.exception.UserNotFoundException;
import com.quiz.darkhold.user.repository.RoleRepository;
import com.quiz.darkhold.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class UserServiceImpl implements UserService {
    public static final int USERS_PER_PAGE = 5;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final BCryptPasswordEncoder encoder;
    private final FindByIndexNameSessionRepository<?> sessions;

    public UserServiceImpl(final UserRepository userRepository,
                           final RoleRepository roleRepository,
                           final BCryptPasswordEncoder encoder) {
        this(userRepository, roleRepository, encoder, Optional.empty());
    }

    @Autowired
    public UserServiceImpl(final UserRepository userRepository,
                           final RoleRepository roleRepository,
                           final BCryptPasswordEncoder encoder,
                           final Optional<FindByIndexNameSessionRepository<?>> sessions) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.encoder = encoder;
        this.sessions = sessions.orElse(null);
    }

    @Override
    public User save(final User user) {
        if (isUpdatingUser(user)) {
            user.setPassword(getPassword(user, findExistingUser(user.getId())));
        } else {
            user.setPassword(encodeNewPassword(user.getPassword()));
        }
        User saved = userRepository.save(user);
        if (isUpdatingUser(user) && !Boolean.TRUE.equals(saved.getEnabled())) {
            invalidateSessions(saved.getId());
        }
        return saved;
    }

    private String encodeNewPassword(final String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required for a new user");
        }
        return encodePassword(password);
    }

    private boolean isUpdatingUser(final User user) {
        return user.getId() != null;
    }

    private User findExistingUser(final Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Could not find any user with the id " + id));
    }

    private String getPassword(final User newUser, final User existingUser) {
        String password = newUser.getPassword();
        return (password == null || password.isEmpty()) ? existingUser.getPassword() : encodePassword(password);
    }

    private String encodePassword(final String password) {
        return encoder.encode(password);
    }

    @Override
    public User findByUsername(final String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public Page<User> getAllUsers(final int pageNumber) {
        Pageable pageable = PageRequest.of(pageNumber - 1, USERS_PER_PAGE);
        return userRepository.findAll(pageable);
    }

    @Override
    public Page<User> searchUsers(final int pageNumber, final String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return getAllUsers(pageNumber);
        }
        Pageable pageable = PageRequest.of(pageNumber - 1, USERS_PER_PAGE);
        return userRepository.search(keyword.trim(), pageable);
    }

    @Override
    public List<Role> listRoles() {
        return (List<Role>) roleRepository.findAll();
    }

    @Override
    public Boolean isEmailUnique(final Long id, final String email) {
        User userByEmail = userRepository.findByEmail(email);
        if (userByEmail == null) {
            return true;
        }
        return isCreatingNew(id) ? false : userByEmail.getId().equals(id);
    }

    private boolean isCreatingNew(final Long id) {
        return id == null;
    }

    @Override
    public User get(final Long id) throws UserNotFoundException {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("Could not find any user with the id " + id));
    }

    @Override
    public void delete(final Long id) throws UserNotFoundException {
        Long countById = userRepository.countById(id);
        if (countById == null || countById == 0) {
            throw new UserNotFoundException("Could not find any user with the id " + id);
        }
        userRepository.deleteById(id);
        invalidateSessions(id);
        schedulePhotoCleanup(id);
    }

    @Override
    public void updateUserEnabledStatus(final Long id, final boolean enabled) {
        userRepository.updateEnabledStatus(id, enabled);
        if (!enabled) {
            invalidateSessions(id);
        }
    }

    private void invalidateSessions(final Long id) {
        if (sessions != null) {
            sessions.findByPrincipalName(String.valueOf(id)).keySet().forEach(sessions::deleteById);
        }
    }

    private void schedulePhotoCleanup(final Long id) {
        String dir = "user-photos/" + id;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    FileUploadUtil.deleteDir(dir);
                }
            });
        } else {
            FileUploadUtil.deleteDir(dir);
        }
    }

    @Override
    public void updateProfile(final Long id, final String firstName, final String lastName, final String email,
                              final String photo)
            throws UserNotFoundException {
        User user = get(id);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setEmail(email);
        if (photo != null) {
            user.setPhoto(photo);
        }
    }

    @Override
    public boolean passwordMatches(final Long id, final String rawPassword) throws UserNotFoundException {
        return encoder.matches(rawPassword, get(id).getPassword());
    }

    @Override
    public boolean changePassword(final Long id, final String currentPassword, final String newPassword)
            throws UserNotFoundException {
        if (!passwordMatches(id, currentPassword)) {
            return false;
        }
        get(id).setPassword(encodePassword(newPassword));
        return true;
    }
}
