package com.solar.Quotation.dto.requestdto;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class BankDetailsRequestDto {

    @NotBlank(message = "Account name is required")
    @Size(max = 200, message = "Account name is too long")
    private String accountName;

    @NotBlank(message = "Bank name is required")
    @Size(max = 150, message = "Bank name is too long")
    private String bankName;

    @NotBlank(message = "Account number is required")
    @Pattern(regexp = "^[0-9]{6,20}$", message = "Account number must be 6 to 20 digits")
    private String accountNumber;

    @NotBlank(message = "IFSC is required")
    @Pattern(regexp = "^[A-Z]{4}0[A-Z0-9]{6}$", message = "IFSC is not valid")
    private String ifsc;

    @Size(max = 150, message = "Branch is too long")
    private String branch;

    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getBankName() { return bankName; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public String getIfsc() { return ifsc; }
    public void setIfsc(String ifsc) { this.ifsc = ifsc; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
}
