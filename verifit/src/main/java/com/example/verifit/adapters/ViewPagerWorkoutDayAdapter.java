package com.example.verifit.adapters;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.verifit.R;
import com.example.verifit.model.WorkoutDay;
import com.example.verifit.model.WorkoutExercise;
import com.example.verifit.ui.DayActivity;
import com.example.verifit.ui.ExercisesActivity;
import com.example.verifit.ui.MainActivity;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

public class ViewPagerWorkoutDayAdapter extends RecyclerView.Adapter<ViewPagerWorkoutDayAdapter.WorkoutDayViewHolder> {

    ArrayList<WorkoutDay> Workout_Days;
    Context ct;

    // Constructor
    public ViewPagerWorkoutDayAdapter(Context ct, ArrayList<WorkoutDay> Workout_Days)
    {
        this.Workout_Days = new ArrayList<>(Workout_Days);
        this.ct = ct;
    }

    @NonNull
    @Override
    public WorkoutDayViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        return new WorkoutDayViewHolder(
                LayoutInflater.from(parent.getContext()).inflate(
                        R.layout.item_view_pager,
                        parent,
                        false
                )
        );
    }

    @Override
    public void onBindViewHolder(@NonNull WorkoutDayViewHolder holder, int position)
    {
        // Set Data Method used to be here :D
        String Date_Str1 = Workout_Days.get(position).getDate();

        // Find which exercises were performed that given date
        ArrayList<WorkoutExercise> Today_Execrises = new ArrayList<WorkoutExercise>();
        for(int i = 0; i < MainActivity.dataStorage.getWorkoutDays().size(); i++)
        {
            if(Date_Str1.equals(MainActivity.dataStorage.getWorkoutDays().get(i).getDate()))
            {
                Today_Execrises = MainActivity.dataStorage.getWorkoutDays().get(i).getExercises();
            }
        }

        // Retour Romain 05/09/2026 : sélection multiple + réorganisation, même mécanique
        // que DayActivity - voir ViewPagerExerciseAdapter et le ItemTouchHelper créé une
        // seule fois par ViewHolder ci-dessous (WorkoutDayViewHolder.itemTouchHelper).
        // currentDate est mis à jour à chaque bind pour que le callback de drag (créé une
        // seule fois, dans le constructeur) sache toujours pour quel jour persister.
        holder.currentDate = Date_Str1;

        // Set Recycler View
        ViewPagerExerciseAdapter workoutExerciseAdapter = new ViewPagerExerciseAdapter(ct, Today_Execrises);
        holder.recyclerView_Main.setAdapter(workoutExerciseAdapter);
        holder.recyclerView_Main.setLayoutManager(new LinearLayoutManager(ct));
        workoutExerciseAdapter.setOnStartDragListener(viewHolder -> holder.itemTouchHelper.startDrag(viewHolder));

        // Convert Date To Something Sensible
        try
        {
            Date date1 = new SimpleDateFormat("yyyy-MM-dd").parse(Date_Str1); //potential exception
            DateFormat date2 = new SimpleDateFormat("EEEE");
            DateFormat date3 = new SimpleDateFormat("MMMM dd yyyy");
            String Date_Str2 = date2.format(date1);
            String Date_Str3 = date3.format(date1);
            holder.tv_date.setText(Date_Str2);
            holder.tv_full_date.setText(Date_Str3);
        }
        catch (ParseException e)
        {
            e.printStackTrace();
        }

        // Navigate to Exercises Activity with specific date
        holder.date_bg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view)
            {
//                Intent in = new Intent(ct, DayActivity.class);
//                in.putExtra("date", Date_Str1);
//                ct.startActivity(in);
            }
        });


        holder.img_bt_next.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                System.out.println("Next!");
            }
        });

        holder.img_bt_back.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                System.out.println("Back!");
            }
        });


    }

    @Override
    public int getItemCount() {
        return Workout_Days.size();
    }

    // Magic Happens here. Public : MainActivity (package .ui) doit pouvoir déclarer une
    // variable de ce type pour retrouver la page actuellement affichée dans le
    // ViewPager2 (voir MainActivity.getCurrentDayViewHolder()).
    public static class WorkoutDayViewHolder extends RecyclerView.ViewHolder
    {
        private TextView tv_date;
        private TextView tv_full_date;
        private RecyclerView recyclerView_Main;
        private CardView cardview_viewpager;
        private ConstraintLayout date_bg; // Used for navigating to AddExerciseActivity with date
        private ImageButton img_bt_back;
        private ImageButton img_bt_next;

        // Retour Romain 05/09/2026 : réorganisation par glisser-déposer des exercices de
        // ce jour ("comme FitNotes"). Un seul ItemTouchHelper par ViewHolder, créé ici
        // dans le constructeur (donc une seule fois par ViewHolder recyclé, jamais
        // recréé/rattaché à chaque bind - contrairement à DayActivity où il est recréé à
        // chaque initActivity(), un choix qui ne pose pas de souci vu que DayActivity a
        // une seule RecyclerView pour toute la durée de vie de l'écran, mais qui
        // deviendrait un vrai leak d'OnItemTouchListener ici puisque les ViewHolder sont
        // recyclés en swipant entre les jours). currentDate est mis à jour à chaque bind
        // (voir onBindViewHolder ci-dessus) pour que onMove()/clearView() - qui vivent
        // au-delà d'un seul bind - sachent toujours quel jour et quel adapter sont
        // actuellement affichés dans cette page.
        private String currentDate;
        private int dragStartPosition = -1;
        private final ItemTouchHelper itemTouchHelper;

        public WorkoutDayViewHolder(@NonNull View itemView)
        {
            super(itemView);
            tv_date = itemView.findViewById(R.id.tv_date);
            cardview_viewpager = itemView.findViewById(R.id.cardview_viewpager);
            recyclerView_Main = itemView.findViewById(R.id.recyclerView_Main);
            tv_full_date = itemView.findViewById(R.id.tv_full_date);
            date_bg = itemView.findViewById(R.id.date_bg);
            img_bt_back = itemView.findViewById(R.id.img_bt_back);
            img_bt_next = itemView.findViewById(R.id.img_bt_next);

            itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0)
            {
                @Override
                public boolean onMove(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target)
                {
                    int from = source.getAdapterPosition();
                    int to = target.getAdapterPosition();

                    if (dragStartPosition == -1)
                    {
                        dragStartPosition = from;
                    }

                    ViewPagerExerciseAdapter adapter = getExerciseAdapter();
                    if (adapter != null)
                    {
                        adapter.moveItem(from, to);
                    }
                    return true;
                }

                @Override
                public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction)
                {
                    // Swipe non utilisé - drag uniquement (poignée dédiée).
                }

                @Override
                public boolean isLongPressDragEnabled()
                {
                    return false;
                }

                @Override
                public void clearView(@NonNull RecyclerView rv, @NonNull RecyclerView.ViewHolder viewHolder)
                {
                    super.clearView(rv, viewHolder);

                    int finalPosition = viewHolder.getAdapterPosition();
                    if (dragStartPosition != -1 && finalPosition != -1 && finalPosition != dragStartPosition
                            && currentDate != null)
                    {
                        persistExerciseOrder(currentDate, dragStartPosition, finalPosition);
                    }
                    dragStartPosition = -1;
                }
            });
            itemTouchHelper.attachToRecyclerView(recyclerView_Main);
        }

        // L'adapter actuellement affiché dans cette page - change à chaque bind (voir
        // onBindViewHolder), donc jamais mis en cache : toujours relu depuis la
        // RecyclerView au moment où on en a besoin. Public : MainActivity (package .ui,
        // différent de .adapters) doit pouvoir y accéder pour démarrer le mode sélection
        // sur la page actuellement affichée dans le ViewPager2 (voir MainActivity).
        public ViewPagerExerciseAdapter getExerciseAdapter()
        {
            RecyclerView.Adapter<?> adapter = recyclerView_Main.getAdapter();
            return (adapter instanceof ViewPagerExerciseAdapter) ? (ViewPagerExerciseAdapter) adapter : null;
        }

        public String getCurrentDate()
        {
            return currentDate;
        }

        // Persiste l'ordre final d'un geste de drag - une seule fois par geste (voir
        // clearView() ci-dessus), jamais à chaque étape intermédiaire.
        private void persistExerciseOrder(String date, int fromPosition, int toPosition)
        {
            int day_position = MainActivity.dataStorage.getDayPosition(date);
            if (day_position < 0)
            {
                return;
            }

            WorkoutDay day = MainActivity.dataStorage.getWorkoutDays().get(day_position);
            day.moveExercise(fromPosition, toPosition);
            MainActivity.dataStorage.saveWorkoutData(itemView.getContext());

            MainActivity.autoBackupRequired = true;
            com.example.verifit.SharedPreferences sharedPreferences =
                    new com.example.verifit.SharedPreferences(itemView.getContext());
            sharedPreferences.save("true", "autoBackupRequired");
        }
    }
}
