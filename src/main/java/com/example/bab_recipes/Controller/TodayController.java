package com.example.bab_recipes.Controller;

import com.example.bab_recipes.DTO.RecipeDTO;
import com.example.bab_recipes.Domain.Bookmark;
import com.example.bab_recipes.Domain.MongoRecipe;
import com.example.bab_recipes.Domain.User;
import com.example.bab_recipes.Service.BookMarkService;
import com.example.bab_recipes.Service.RatingService;
import com.example.bab_recipes.Service.TodayService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class TodayController {

    @Autowired
    private TodayService todayService;

    @Autowired
    private BookMarkService markService;

    @Autowired
    private RatingService ratingService;

    @GetMapping("/Today")
    public String Today_eat(HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");

        if (user != null) {
            model.addAttribute("email", user.getUserEmail());
            System.out.println("userEmail: " + user.getUserEmail());
        } else {
            System.out.println("유저 로딩 실패");
        }

        if (user == null) {
            return "redirect:/login";
        }

        return "/Today_eat";
    }

    @PostMapping("/search")
    public String todayEatSearch(@RequestParam(value = "fridgeTags", required = false) String fridgeTags,
                                 @RequestParam(value = "excludeTags", required = false) String excludeTags,
                                 HttpSession session, Model model) {
        User user = (User) session.getAttribute("user");

        if (user != null) {
            model.addAttribute("email", user.getUserEmail());
            System.out.println("userEmail: " + user.getUserEmail());
        } else {
            System.out.println("유저 로딩 실패");
        }

        if (user == null) {
            return "redirect:/login";
        }

        session.removeAttribute("fridgeItems");
        session.removeAttribute("excludedItems");
        session.removeAttribute("recipes");
        session.removeAttribute("bookmarkedRecipe");

        String[] fridgeItemsArray = {};
        String[] excludeItemsArray = {};

        if (fridgeTags != null && !fridgeTags.isEmpty()) {
            fridgeItemsArray = fridgeTags.split(",");
        }

        if (excludeTags == null || excludeTags.isEmpty()) {
            System.out.println("if (searchOne): " + fridgeTags);
            List<MongoRecipe> recipes = todayService.searchOne(fridgeItemsArray);

            session.setAttribute("fridgeItems", Arrays.asList(fridgeItemsArray));
            session.setAttribute("recipes", recipes);
            return "redirect:/Today_eat_detail";
        } else {
            System.out.println("else (searchRecipes): " + fridgeTags + ", " + excludeTags);
            excludeItemsArray = excludeTags.split(",");
            List<MongoRecipe> recipes = todayService.searchRecipes(fridgeItemsArray, excludeItemsArray);

            session.setAttribute("fridgeItems", Arrays.asList(fridgeItemsArray));
            session.setAttribute("excludedItems", Arrays.asList(excludeItemsArray));
            session.setAttribute("recipes", recipes);
            return "redirect:/Today_eat_detail";
        }
    }

    @GetMapping("/Today_eat_detail")
    public String Today_eatDetail(Model model, HttpSession session) {
        // 세션에서 데이터를 가져옴
        List<String> fridgeItems = (List<String>) session.getAttribute("fridgeItems");
        List<String> excludeItems = (List<String>) session.getAttribute("excludedItems");
        List<MongoRecipe> recipes = (List<MongoRecipe>) session.getAttribute("recipes");
        User user = (User) session.getAttribute("user");

        if (user != null) {
            model.addAttribute("email", user.getUserEmail());
            session.removeAttribute("recipe");
            session.removeAttribute("bookmark");
            System.out.println("userEmail: " + user.getUserEmail());
        } else{
            System.out.println("유저 로딩 실패");
            return "redirect:/login";
        }

        if (recipes==null) {
            return "redirect:/main";
        }

        // MongoDB에서 레시피 ID 목록 추출
        List<String> recipeIds = recipes.stream()
                .map(MongoRecipe::getId)
                .collect(Collectors.toList());

        // MySQL에서 북마크 정보 조회
        List<Bookmark> bookmarks = markService.getAllBookmarks(recipeIds, user);

        // 북마크된 레시피 ID 목록 생성
        Set<String> bookmarkedRecipeIds = bookmarks.stream()
                .map(Bookmark::getRecipeId)
                .collect(Collectors.toSet());

        // 레시피와 북마크 정보를 결합하여 DTO 생성
        List<RecipeDTO> recipeDto = recipes.stream()
                .map(recipe -> new RecipeDTO(
                        recipe.getId(),
                        recipe.getTitle(),
                        recipe.getIngredients(),
                        recipe.getMediaUrl(),
                        bookmarkedRecipeIds.contains(recipe.getId()) // 북마크 여부 설정
                ))
                .collect(Collectors.toList());

        // 모델에 데이터 추가
        model.addAttribute("fridgeItems", fridgeItems);
        model.addAttribute("excludeItems", excludeItems);
        model.addAttribute("recipes", recipeDto);
        session.setAttribute("bookmarkedRecipe", recipeDto);


        return "/Today_eat_detail";
    }


    @GetMapping("/recipe/detail/{recipeId}")
    public String recipeDetail(@PathVariable("recipeId") Optional<String> recipeId, HttpSession session, Model model) {
        String id = recipeId.orElse("");
        User user = (User) session.getAttribute("user");

        if (user != null) {
            model.addAttribute("email", user.getUserEmail());
            System.out.println("userEmail: " + user.getUserEmail());
        } else {
            System.out.println("유저 로딩 실패");
        }

        if (user == null) {
            return "redirect:/login";
        }

        Optional<MongoRecipe> recipeOptional = todayService.detailRecipe(id);
        Optional<Bookmark> bookmarkOptional = markService.statusMark(id, user.getUserId());
        if (recipeOptional.isPresent()) {
            MongoRecipe recipe = recipeOptional.get();
            Bookmark bookmark = bookmarkOptional.orElse(new Bookmark(0)); // 북마크가 없을 때 기본값 설정
            System.out.println("isbook? : " + bookmark.getIsBookmark());

            session.setAttribute("recipe", recipe);
            session.setAttribute("bookmark", bookmark);
            return "redirect:/Recipes_detail";
        }

        return "redirect:/Today_eat_detail";
    }

    @GetMapping("/Recipes_detail")
    public String recipeDetail(Model model, HttpSession session) {
        User user = (User) session.getAttribute("user");

        if (user != null) {
            model.addAttribute("email", user.getUserEmail());
            System.out.println("userEmail: " + user.getUserEmail());
        } else {
            System.out.println("유저 로딩 실패");
        }

        if (user == null) {
            return "redirect:/login";
        }

        MongoRecipe recipe = (MongoRecipe) session.getAttribute("recipe");
        Bookmark bookmark = (Bookmark) session.getAttribute("bookmark");


        if (recipe != null) {
            Map<String, Double> ratings = ratingService.getRatings(recipe.getId());
            //현재 데이터
            double easy = ratings.get("easy"); //4
            double hard = ratings.get("hard"); //0
            double count = ratings.get("count"); //1

            double easyPercent = easy * 100;
            double hardPercent = hard * 100;

            model.addAttribute("recipe", recipe);
            model.addAttribute("bookmark", bookmark);
            model.addAttribute("hard", hardPercent);
            model.addAttribute("easy", easyPercent);

            return "Recipes_detail";
        }

        return "redirect:/Today_eat_detail";

    }
}
