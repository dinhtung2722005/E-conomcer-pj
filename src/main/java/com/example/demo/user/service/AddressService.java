package com.example.demo.user.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.user.api.dto.AddressRequest;
import com.example.demo.user.api.dto.AddressResponse;
import com.example.demo.user.entity.Address;
import com.example.demo.user.entity.User;
import com.example.demo.shared.error.AppException;
import com.example.demo.shared.error.ErrorCode;
import com.example.demo.user.repository.AddressRepository;
import com.example.demo.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AddressResponse> getUserAddresses(String username) {
        User user = findUser(username);
        return addressRepository.findByUser(user).stream()
                .map(AddressService::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public AddressResponse addAddress(String username, AddressRequest request) {
        User user = findUser(username);

        boolean isFirstAddress = addressRepository.findByUser(user).isEmpty();
        boolean willBeDefault = request.isDefault() || isFirstAddress;

        if (willBeDefault) {
            addressRepository.resetDefaultAddressForUser(user);
        }

        Address newAddress = Address.builder()
                .user(user)
                .recipientName(request.getRecipientName())
                .phone(request.getPhoneNumber())
                .street(request.getStreet())
                .ward(request.getWard())
                .district(request.getDistrict())
                .city(request.getCity())
                .isDefault(willBeDefault)
                .build();

        return toResponse(addressRepository.save(newAddress));
    }

    @Transactional
    public AddressResponse updateAddress(String username, Long addressId, AddressRequest request) {
        User user = findUser(username);
        Address address = findOwnedAddress(addressId, user);

        boolean isCurrentlyDefault = address.isDefault();
        boolean wantsToBeDefault = request.isDefault();

        if (isCurrentlyDefault && !wantsToBeDefault) {
            throw new AppException(ErrorCode.ADDRESS_DEFAULT_REQUIRED);
        }

        if (!isCurrentlyDefault && wantsToBeDefault) {
            addressRepository.resetDefaultAddressForUser(user);
            address.setDefault(true);
        }

        address.setRecipientName(request.getRecipientName());
        address.setPhone(request.getPhoneNumber());
        address.setStreet(request.getStreet());
        address.setWard(request.getWard());
        address.setDistrict(request.getDistrict());
        address.setCity(request.getCity());

        return toResponse(addressRepository.save(address));
    }

    @Transactional
    public void deleteAddress(String username, Long addressId) {
        User user = findUser(username);
        Address address = findOwnedAddress(addressId, user);

        if (address.isDefault()) {
            throw new AppException(ErrorCode.ADDRESS_DEFAULT_CANNOT_DELETE);
        }

        addressRepository.delete(address);
    }

    private User findUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    /** Luôn truy vấn theo cặp (id, chủ sở hữu) để không thao tác được địa chỉ của người khác. */
    private Address findOwnedAddress(Long addressId, User user) {
        return addressRepository.findByIdAndUser(addressId, user)
                .orElseThrow(() -> new AppException(ErrorCode.ADDRESS_NOT_FOUND));
    }

    private static AddressResponse toResponse(Address address) {
        String fullAddress = address.getStreet() + ", "
                + address.getWard() + ", "
                + address.getDistrict() + ", "
                + address.getCity();

        return AddressResponse.builder()
                .id(address.getId())
                .recipientName(address.getRecipientName())
                .phoneNumber(address.getPhone())
                .fullAddress(fullAddress)
                .isDefault(address.isDefault())
                .build();
    }
}
