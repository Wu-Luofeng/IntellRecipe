package com.springboot.intellrecipe.diet.service;

import com.springboot.intellrecipe.diet.dto.AddEntryDTO;
import com.springboot.intellrecipe.diet.dto.TodayDietVO;

public interface DietService {
    TodayDietVO getToday(Long userId);
    Long addEntry(Long userId, AddEntryDTO dto);
    void removeEntry(Long userId, Long entryId);
}
