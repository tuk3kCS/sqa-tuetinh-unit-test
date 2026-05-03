package com.example.AuthService.dto.response;

import lombok.*;
import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserProfileResponse {
    private String email;
    private String name;
    private String gender;
    private LocalDate dateOfBirth;
    private String phoneNumber;
    private String photoUrl;
    private String facebookAccountId;
    private String googleAccountId;
    private String roleName;
    private String countryName;
}
