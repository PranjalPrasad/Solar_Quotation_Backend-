package com.solar.Quotation.service.serviceImpl;

import com.solar.Quotation.dto.requestdto.LoginRequest;
import com.solar.Quotation.dto.responsedto.LoginResponse;
import com.solar.Quotation.entity.User;
import com.solar.Quotation.exception.InvalidCredentialsException;
import com.solar.Quotation.repository.UserRepository;
import com.solar.Quotation.security.JwtUtil;
import com.solar.Quotation.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public LoginResponse login(LoginRequest request) {

        if (request.getUsername() == null || request.getPassword() == null) {
            throw new InvalidCredentialsException("Username and password are required");
        }

        Optional<User> userOpt = userRepository.findByUsername(request.getUsername().trim());

        if (userOpt.isEmpty()) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        User user = userOpt.get();

        if (!"Active".equalsIgnoreCase(user.getStatus())) {
            throw new InvalidCredentialsException("This account is inactive. Contact your admin.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.getPartnerId()
        );

        return new LoginResponse(
                token,
                user.getId(),
                user.getUsername(),
                user.getRole().name(),
                user.getPartnerId(),
                user.getPartnerName(),
                user.getName()
        );
    }
}

