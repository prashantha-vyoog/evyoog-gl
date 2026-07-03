package com.evyoog.gl.calendar.dto;

import jakarta.validation.constraints.Size;

public record UpdateCalendarRequest(

        @Size(max = 255, message = "name must be at most 255 characters")
        String name,

        @Size(max = 500, message = "description must be at most 500 characters")
        String description
) {
}
