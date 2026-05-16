import com.gto.recipesearch.IntLongMap;
import com.gto.recipesearch.IntMapContainer;

public class Recipe {

    final IntLongMap input;
    IntMapContainer container;

    public Recipe(IntLongMap input) {
        this.input = input;
    }

    @Override
    public String toString() {
        return input.toString();
    }
}
