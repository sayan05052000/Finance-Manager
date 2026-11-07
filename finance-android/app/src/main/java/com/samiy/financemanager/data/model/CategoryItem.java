package com.samiy.financemanager.data.model;

import com.google.gson.annotations.SerializedName;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class CategoryItem implements Serializable {
    @SerializedName("name")
    public String name = "";

    @SerializedName("subCategories")
    public List<String> subCategories = new ArrayList<>();

    public CategoryItem() {}

    public CategoryItem(String name) {
        this.name = name;
    }
}
