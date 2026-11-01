package com.example.demo.shared.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springdoc.core.annotations.ParameterObject;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/** Tham số phân trang của các API quản trị; trang đầu tiên là 1. */
@Getter
@Setter
@ParameterObject
public class PageQuery {
    @Min(value = 1, message = "page phải từ 1 trở lên")
    @Schema(description = "Số trang, bắt đầu từ 1", defaultValue = "1", example = "1", minimum = "1")
    private int page = 1;

    @Min(value = 1, message = "size phải từ 1 trở lên")
    @Max(value = 100, message = "size tối đa 100")
    @Schema(description = "Số phần tử mỗi trang, tối đa 100", defaultValue = "10", example = "10", minimum = "1", maximum = "100")
    private int size = 10;

    public Pageable toPageable() {
        return PageRequest.of(page - 1, size, Sort.by("id").descending());
    }
}
