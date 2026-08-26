package com.example.demo.user.service;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.user.dto_request.AddressRequest;
import com.example.demo.user.dto_request.AddressResponse;
import com.example.demo.user.entity.Address;
import com.example.demo.user.entity.User;
import com.example.demo.user.exception.AppException;
import com.example.demo.user.exception.ErrorCode;
import com.example.demo.user.repository.AddressRepository;
import com.example.demo.user.repository.UserRepository;
@Service
public class AddressService {
    @Autowired
    private AddressRepository addressRepository;
    
    @Autowired
    private UserRepository userRepository;
   
    private AddressResponse convertToResponse(Address address) {
        String fullAddress = address.getStreet() + ", " + 
                             address.getWard() + ", " + 
                             address.getDistrict() + ", " + 
                             address.getCity();

        return AddressResponse.builder()
                .id(address.getId())
                .recipientName(address.getRecipientName())
                .phoneNumber(address.getPhone())
                .fullAddress(fullAddress)
                .isDefault(address.isDefault())
                .build();
    }


    public List<AddressResponse> getUserAddresses(String username) {
        // Bước 1: Tìm người dùng trong DB
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
                
        // Bước 2: Lấy danh sách địa chỉ, chuyển sang DTO và trả về
        List<Address> addresses = addressRepository.findByUser(user);
        
        return addresses.stream()
                .map(address -> convertToResponse(address))
                .collect(Collectors.toList());
    }

    @Transactional
    public AddressResponse addAddress(String username, AddressRequest request) {
        // Bước 1: Tìm người dùng
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

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

      
        Address savedAddress = addressRepository.save(newAddress);
        return convertToResponse(savedAddress);
    }

 
    @Transactional
    public AddressResponse updateAddress(String username, Long addressId, AddressRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Address existingAddress = addressRepository.findByIdAndUser(addressId, user)
                .orElseThrow(() -> new RuntimeException("Địa chỉ không tồn tại hoặc bạn không có quyền sửa!"));

        boolean isCurrentlyDefault = existingAddress.isDefault();
        boolean wantsToBeDefault = request.isDefault();

        if (isCurrentlyDefault && !wantsToBeDefault) {
             throw new RuntimeException("Vui lòng chọn một địa chỉ khác làm mặc định trước!");
        }

       
        if (!isCurrentlyDefault && wantsToBeDefault) {
            addressRepository.resetDefaultAddressForUser(user);
            existingAddress.setDefault(true);
        }

        existingAddress.setRecipientName(request.getRecipientName());
        existingAddress.setPhone(request.getPhoneNumber());
        existingAddress.setStreet(request.getStreet());
        existingAddress.setWard(request.getWard());
        existingAddress.setDistrict(request.getDistrict());
        existingAddress.setCity(request.getCity());
        
        Address updatedAddress = addressRepository.save(existingAddress);
        return convertToResponse(updatedAddress);
    }

   
    @Transactional
    public void deleteAddress(String username, Long addressId) {
        // Bước 1: Tìm người dùng
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        // Bước 2: Tìm địa chỉ cần xóa
        Address addressToDelete = addressRepository.findByIdAndUser(addressId, user)
                .orElseThrow(() -> new AppException(ErrorCode.INTERNAL_SERVER_ERROR));

        // Bước 3: Kiểm tra an toàn - Cấm xóa địa chỉ đang là mặc định
        if (addressToDelete.isDefault()) {
            throw new RuntimeException("Không thể xóa địa chỉ mặc định. Vui lòng chọn địa chỉ khác làm mặc định trước khi xóa!");
        }

        // Bước 4: Thực hiện xóa
        addressRepository.delete(addressToDelete);
    }
    
}
