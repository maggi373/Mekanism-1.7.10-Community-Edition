package mekanism.client.jei.machine.other;

import mekanism.client.jei.MekanismJEI;
import mekanism.client.jei.machine.MekanismRecipeWrapper;
import mekanism.common.recipe.machines.IsotopicCentrifugeRecipe;
import mezz.jei.api.ingredients.IIngredients;

public class IsotopicCentrifugeRecipeWrapper<RECIPE extends IsotopicCentrifugeRecipe> extends MekanismRecipeWrapper<RECIPE> {

    public IsotopicCentrifugeRecipeWrapper(RECIPE recipe) {
        super(recipe);
    }

    @Override
    public void getIngredients(IIngredients ingredients) {
        ingredients.setInput(MekanismJEI.TYPE_GAS, recipe.getInput().ingredient);
        ingredients.setOutput(MekanismJEI.TYPE_GAS, recipe.getOutput().output);
    }
}