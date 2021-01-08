package com.example.myproduct.server;

import com.example.myproduct.admin.config.AdminController;
import com.example.myproduct.admin.config.VersionRange;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class MyProductAdminController {

    @GetMapping("/admin")
    public String adminForm(Model model) {
        model.addAttribute("versionRange", new VersionRange());
        return "admin";
    }

    @PostMapping("/admin")
    public String adminSubmit(@ModelAttribute VersionRange versionRange, Model model) {
        AdminController.INSTANCE.update(versionRange);
        model.addAttribute("versionRange", versionRange);
        return "admin";
    }

}
