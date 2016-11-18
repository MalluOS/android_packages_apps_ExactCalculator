/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.calculator2;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toolbar;

import java.util.ArrayList;

public class HistoryFragment extends Fragment {

    public static final String TAG = "HistoryFragment";

    private final DragLayout.DragCallback mDragCallback =
            new DragLayout.DragCallback() {
                @Override
                public void onStartDragging() {
                    // no-op
                }

                @Override
                public void whileDragging(float yFraction) {
                    mDragController.animateViews(yFraction, mRecyclerView, mAdapter.getItemCount());
                }

                @Override
                public void onClosed() {
                    // TODO: only cancel historical evaluations
                    mEvaluator.cancelAll(true);

                    mDragController.resetAnimationInitialized();
                }

                @Override
                public boolean allowDrag(MotionEvent event) {
                    // Do not allow drag if the recycler view can move down more
                    return !mRecyclerView.canScrollVertically(1);
                }

                @Override
                public boolean shouldInterceptTouchEvent(MotionEvent event) {
                    return true;
                }

                @Override
                public int getDisplayHeight() {
                    return 0;
                }

                @Override
                public void onLayout(int translation) {
                    // no-op
                }
            };

    private final DragController mDragController = new DragController();

    private RecyclerView mRecyclerView;
    private HistoryAdapter mAdapter;

    private Evaluator mEvaluator;

    private ArrayList<HistoryItem> mDataSet = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mAdapter = new HistoryAdapter((Calculator) getActivity(), mDataSet,
                getContext().getResources().getString(R.string.title_current_expression));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View view = inflater.inflate(
                R.layout.fragment_history, container, false /* attachToRoot */);

        mRecyclerView = (RecyclerView) view.findViewById(R.id.history_recycler_view);

        // The size of the RecyclerView is not affected by the adapter's contents.
        mRecyclerView.setAdapter(mAdapter);

        final Toolbar toolbar = (Toolbar) view.findViewById(R.id.history_toolbar);
        toolbar.inflateMenu(R.menu.fragment_history);
        toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.menu_clear_history) {
                    clearHistory();
                    return true;
                }
                return onOptionsItemSelected(item);
            }
        });
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().onBackPressed();
            }
        });

        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final DragLayout dragLayout = (DragLayout) getActivity().findViewById(R.id.drag_layout);
        dragLayout.removeDragCallback(mDragCallback);
        dragLayout.addDragCallback(mDragCallback);

        mEvaluator = Evaluator.getInstance((Calculator) getActivity());

        if (mEvaluator != null) {
            initializeController();

            final long maxIndex = mEvaluator.getMaxIndex();

            final ArrayList<HistoryItem> newDataSet = new ArrayList<>();

            if (!mEvaluator.getExpr(Evaluator.MAIN_INDEX).isEmpty()) {
                // Add the current expression as the first element in the list (the layout is reversed
                // and we want the current expression to be the last one in the recyclerview).
                newDataSet.add(new HistoryItem(Evaluator.MAIN_INDEX, 0 /* millis*/,
                        mEvaluator.getExprAsSpannable(0)));
            }
            // We retrieve the current expression separately, so it's excluded from this loop.
            // We lazy-fill, so just retrieve the first 25 expressions for now.
            for (long i = Math.min(maxIndex, 25); i > 0; --i) {
                final HistoryItem item = new HistoryItem(i, mEvaluator.getTimeStamp(i),
                        mEvaluator.getExprAsSpannable(i));
                newDataSet.add(item);
            }
            for (long i = Math.max(maxIndex - 25, 0); i > 0; --i) {
                newDataSet.add(null);
            }
            if (maxIndex == 0) {
                newDataSet.add(new HistoryItem());
            }
            mDataSet = newDataSet;
            mAdapter.setDataSet(mDataSet);
        }

        mAdapter.notifyDataSetChanged();

        // Initialize the current expression element to dimensions that match the display to
        // avoid flickering and scrolling when elements expand on drag start.
        mDragController.animateViews(1.0f, mRecyclerView, mAdapter.getItemCount());
    }

    @Override
    public Animator onCreateAnimator(int transit, boolean enter, int nextAnim) {
        final View view = getView();
        final int height = getResources().getDisplayMetrics().heightPixels;
        if (!enter) {
            return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -height);
        } else if (transit == FragmentTransaction.TRANSIT_FRAGMENT_OPEN) {
            return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, -height, 0f);
        }
        return null;
    }

    @Override
    public void onDestroyView() {
        final DragLayout dragLayout = (DragLayout) getActivity().findViewById(R.id.drag_layout);
        if (dragLayout != null) {
            dragLayout.removeDragCallback(mDragCallback);
        }

        mEvaluator.cancelAll(true);
        super.onDestroy();
    }

    private void initializeController() {
        mDragController.setDisplayFormula(
                (CalculatorFormula) getActivity().findViewById(R.id.formula));

        mDragController.setDisplayResult(
                (CalculatorResult) getActivity().findViewById(R.id.result));

        mDragController.setToolbar(getActivity().findViewById(R.id.toolbar));

        mDragController.setEvaluator(mEvaluator);
    }

    private void clearHistory() {
        // TODO: Try to preserve the current, saved, and memory expressions. How should we
        // handle expressions to which they refer?
        // FIXME: This should clearly happen on a background thread.
        mEvaluator.clearEverything();
        // TODO: It's not clear what we should really do here. This is an initial hack.
        // May want to make onClearAnimationEnd() private if/when we fix this.
        Calculator calculator = (Calculator) getActivity();
        calculator.onClearAnimationEnd();
        calculator.onBackPressed();
    }
}
