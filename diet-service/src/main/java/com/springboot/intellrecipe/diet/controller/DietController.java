package com.springboot.intellrecipe.diet.controller;

import com.springboot.intellrecipe.common.dto.Result;
import com.springboot.intellrecipe.common.dto.UserDTO;
import com.springboot.intellrecipe.common.utils.UserHolder;
import com.springboot.intellrecipe.diet.dto.AddEntryDTO;
import com.springboot.intellrecipe.diet.dto.TodayDietVO;
import com.springboot.intellrecipe.diet.service.DietService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/diet")
@RequiredArgsConstructor
public class DietController {

    private final DietService dietService;

    /** 获取今日食谱 */
    @GetMapping("/today")
    public Result<TodayDietVO> getToday() {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        return Result.ok(dietService.getToday(user.getId()));
    }

    /** 添加食材条目 */
    @PostMapping("/entry")
    public Result<Long> addEntry(@RequestBody AddEntryDTO dto) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        try {
            Long id = dietService.addEntry(user.getId(), dto);
            return Result.ok(id);
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }

    /** 删除食材条目 */
    @DeleteMapping("/entry/{id}")
    public Result<Void> removeEntry(@PathVariable Long id) {
        UserDTO user = UserHolder.getUser();
        if (user == null) return Result.fail("请先登录");
        try {
            dietService.removeEntry(user.getId(), id);
            return Result.ok();
        } catch (IllegalArgumentException e) {
            return Result.fail(e.getMessage());
        }
    }
}
