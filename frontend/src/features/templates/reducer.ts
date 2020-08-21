import {createSlice, PayloadAction} from 'redux-starter-kit';
import {Category} from './interface';

export type GetCategoriesAction = {};

export type CategoriesAction = {
    categories: Category[];
};

export type AddCategoryAction = {
    name: string;
    description: string;
};

let initialState = {
    categories: [] as Category[],
};

const slice = createSlice({
    name: 'templates',
    initialState,
    reducers: {
        categoriesReceived: (state, action: PayloadAction<CategoriesAction>) => {
            const {categories} = action.payload;
            state.categories = categories;
        },
        getCategories: (state, action: PayloadAction<GetCategoriesAction>) => state,
        addCategory: (state, action: PayloadAction<AddCategoryAction>) => state
    },
});

export const reducer = slice.reducer;
export const actions = slice.actions;
