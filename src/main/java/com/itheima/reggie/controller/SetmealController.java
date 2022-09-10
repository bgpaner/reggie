package com.itheima.reggie.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.itheima.reggie.common.R;
import com.itheima.reggie.dto.DishDto;
import com.itheima.reggie.dto.SetmealDto;
import com.itheima.reggie.entity.Category;
import com.itheima.reggie.entity.Dish;
import com.itheima.reggie.entity.Setmeal;
import com.itheima.reggie.entity.SetmealDish;
import com.itheima.reggie.mapper.DishMapper;
import com.itheima.reggie.mapper.SetmealDishMapper;
import com.itheima.reggie.service.CategoryService;
import com.itheima.reggie.service.DishService;
import com.itheima.reggie.service.SetmealDishService;
import com.itheima.reggie.service.SetmealService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 套餐管理
 */

@RestController
@RequestMapping("/setmeal")
@Slf4j
public class SetmealController {

    @Autowired
    private SetmealService setmealService;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private SetmealDishService setmealDishService;

    @Autowired
    private DishService dishService;

    @Autowired
    private DishMapper dishMapper;

    @Autowired
    private SetmealDishMapper setmealDishMapper;


    /**
     * 新增套餐
     * @param setmealDto
     * @return
     */
    @PostMapping
    @CacheEvict(value = "setmealCache",allEntries = true)
    public R<String> save(@RequestBody SetmealDto setmealDto){
        log.info("套餐信息：{}",setmealDto);

        setmealService.saveWithDish(setmealDto);

        return R.success("新增套餐成功");
    }

    /**
     * 套餐分页查询
     * @param page
     * @param pageSize
     * @param name
     * @return
     */
    @GetMapping("/page")
    public R<Page> page(int page,int pageSize,String name){
        //分页构造器对象
        Page<Setmeal> pageInfo = new Page<>(page,pageSize);
        Page<SetmealDto> dtoPage = new Page<>();

        LambdaQueryWrapper<Setmeal> queryWrapper = new LambdaQueryWrapper<>();
        //添加查询条件，根据name进行like模糊查询
        queryWrapper.like(name != null,Setmeal::getName,name);
        //添加排序条件，根据更新时间降序排列
        queryWrapper.orderByDesc(Setmeal::getUpdateTime);

        setmealService.page(pageInfo,queryWrapper);

        //对象拷贝
        BeanUtils.copyProperties(pageInfo,dtoPage,"records");
        List<Setmeal> records = pageInfo.getRecords();

        List<SetmealDto> list = records.stream().map((item) -> {
            SetmealDto setmealDto = new SetmealDto();
            //对象拷贝
            BeanUtils.copyProperties(item,setmealDto);
            //分类id
            Long categoryId = item.getCategoryId();
            //根据分类id查询分类对象
            Category category = categoryService.getById(categoryId);
            if(category != null){
                //分类名称
                String categoryName = category.getName();
                setmealDto.setCategoryName(categoryName);
            }
            return setmealDto;
        }).collect(Collectors.toList());

        dtoPage.setRecords(list);
        return R.success(dtoPage);
    }

    /**
     * 删除套餐
     * @param ids
     * @return
     */
    @DeleteMapping
    @CacheEvict(value = "setmealCache",allEntries = true)
    public R<String> delete(@RequestParam List<Long> ids){
        log.info("ids:{}",ids);

        setmealService.removeWithDish(ids);

        return R.success("套餐数据删除成功");
    }

    /**
     * 更新套餐状态
     * @param ids
     * @return
     */
    @PostMapping("/status/{status}")
    public R<String> updateStatus(@PathVariable int status,@RequestParam List<Long> ids){
        //获取修改状态的套餐List
        LambdaQueryWrapper<Setmeal> queryWrapper=new LambdaQueryWrapper<>();
        queryWrapper.in(Setmeal::getId,ids);
        List<Setmeal> list=setmealService.list(queryWrapper);
        LambdaQueryWrapper<SetmealDish> queryWrapper1=new LambdaQueryWrapper<>();
        //接到起售请求，检查套餐菜品是否包含未起售菜品
        if (status==1){
            //根据套餐的Id查询套餐包含的菜品
            for (Setmeal setmeal1 : list) {
                queryWrapper1.eq(SetmealDish::getSetmealId,setmeal1.getId());
                List<SetmealDish> list_SetmealDish=setmealDishService.list(queryWrapper1);
                //根据菜品的Id查询菜品的状态
                for (SetmealDish list_setmealDish : list_SetmealDish) {
                    Dish dish=dishService.getById(list_setmealDish.getDishId());
                    if(dish.getStatus()==0){
                        log.info("套餐包含未起售菜品！");
                        return R.error("套餐包含未起售菜品！");
                    }
                }
            }
        }
        for (Setmeal setmeal : list) {
            setmeal.setStatus(status);
            setmealService.updateById(setmeal);
        }
        return R.success("套餐更改状态成功！");
    }

    /**
     * 根据条件查询套餐数据
     * @param setmeal
     * @return
     */
    @GetMapping("/list")
    @Cacheable(value = "setmealCache",key="#setmeal.categoryId+'_'+#setmeal.getId()")
    public R<List<Setmeal>> list(Setmeal setmeal){
        LambdaQueryWrapper<Setmeal> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(setmeal.getCategoryId() != null,Setmeal::getCategoryId,setmeal.getCategoryId());
        queryWrapper.eq(setmeal.getStatus() != null,Setmeal::getStatus,setmeal.getStatus());
        queryWrapper.orderByDesc(Setmeal::getUpdateTime);

        List<Setmeal> list = setmealService.list(queryWrapper);

        return R.success(list);
    }

    /**
     * 修改套餐数据,回显要修改的套餐原始数据
     */
    @GetMapping("/{id}")
    public R<SetmealDto> update(@PathVariable Long id){
        Setmeal setmeal=setmealService.getById(id);
        //获取套餐的详细菜品;
        LambdaQueryWrapper<SetmealDish> queryWrapper=new LambdaQueryWrapper<>();
        queryWrapper.eq(SetmealDish::getSetmealId,setmeal.getId());
        List<SetmealDish> list=setmealDishService.list(queryWrapper);
        SetmealDto setmealDto=new SetmealDto();
        //赋值SetmealDto的菜品参数;
        setmealDto.setSetmealDishes(list);

        //复制套餐的其他参数到setmealDto;
        BeanUtils.copyProperties(setmeal,setmealDto);
        return R.success(setmealDto);
    }

    /**
     * 保持修改后的套餐数据
     */
    @PutMapping
    @CacheEvict(value = "setmealCache",allEntries = true)
    public R<String> saveSetmeal(@RequestBody SetmealDto setmealDto){
        List<SetmealDish> dishList=setmealDto.getSetmealDishes();
        Setmeal setmeal=new Setmeal();
        BeanUtils.copyProperties(setmealDto,setmeal);
        setmealService.updateById(setmeal);
        //删除套餐原来的菜品
        LambdaQueryWrapper<SetmealDish> queryWrapper=new LambdaQueryWrapper<>();
        queryWrapper.eq(SetmealDish::getSetmealId,setmeal.getId());
        setmealDishService.remove(queryWrapper);
        //更新后的菜品赋值
        for (SetmealDish setmealDish : dishList) {
            setmealDish.setSetmealId(setmealDto.getId());
        }
        setmealDishService.saveBatch(dishList);
        return R.success("修改套餐信息成功！");
    }

    /**
     * 套餐详情展示
     * @param id
     * @return
     */
    @GetMapping("/dish/{id}")
    public R<List<DishDto>> getDish(@PathVariable Long id){
        QueryWrapper<SetmealDish> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("setmeal_id", id);
        List<SetmealDish> setmealDishes = setmealDishMapper.selectList(queryWrapper);
        List<Long> list = new ArrayList<>();
        for (SetmealDish item : setmealDishes) {
            list.add(item.getDishId());
        }
        QueryWrapper<Dish> dishQueryWrapper = new QueryWrapper<>();
        dishQueryWrapper.in("id", list);
        List<Dish> dishes = dishMapper.selectList(dishQueryWrapper);
        List<DishDto> dishDtos = dishes.stream().map((item) ->{
            DishDto dishDto = new DishDto();
            BeanUtils.copyProperties(item, dishDto);
            return dishDto;
        }).collect(Collectors.toList());

        for (DishDto dishDto : dishDtos) {
            for (SetmealDish setmealDish : setmealDishes) {
                if(setmealDish.getDishId().equals(dishDto.getId())){
                    dishDto.setCopies(setmealDish.getCopies());
                }
            }
        }

        return R.success(dishDtos);
    }
}
