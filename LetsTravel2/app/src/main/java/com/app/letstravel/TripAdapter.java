package com.app.letstravel;

import android.content.Context;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TripAdapter extends RecyclerView.Adapter<TripAdapter.TripViewHolder> {

    private final Context context;
    private final List<Trip> tripList;
    private final boolean isAdmin;

    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

    public TripAdapter(Context context, List<Trip> trips) {
        this.context = context;
        this.tripList = trips;
        this.isAdmin = context.getSharedPreferences("AuthPrefs", Context.MODE_PRIVATE)
                .getBoolean("isAdmin", false);
    }

    @NonNull
    @Override
    public TripViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.trip_item_layout, parent, false);
        return new TripViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TripViewHolder holder, int position) {
        Trip trip = tripList.get(position);
        holder.tvSubject.setText(trip.getSubject());
        holder.tvVenue.setText("Venue: " + trip.getVenue());
        holder.tvDates.setText("From " + trip.getStartDate() + " to " + trip.getEndDate());
        holder.imagePreviewLayout.removeAllViews();

        List<String> imageBase64List = trip.getImageBase64List();
        if (imageBase64List != null && !imageBase64List.isEmpty()) {
            holder.imagePreviewLayout.setVisibility(View.VISIBLE);
            for (String base64 : imageBase64List) {
                try {
                    byte[] decoded = Base64.decode(base64, Base64.DEFAULT);
                    ImageView img = new ImageView(context);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(200, 200);
                    params.setMargins(8, 8, 8, 8);
                    img.setLayoutParams(params);
                    img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                    img.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(decoded, 0, decoded.length));
                    holder.imagePreviewLayout.addView(img);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            holder.imagePreviewLayout.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> showDetailDialog(trip));

        if (isAdmin) {
            holder.btnFavorite.setVisibility(View.GONE);
            holder.btnJoin.setVisibility(View.GONE);
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> {
                new android.app.AlertDialog.Builder(context)
                        .setTitle("Delete Trip")
                        .setMessage("Are you sure you want to delete this trip?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            FirebaseFirestore.getInstance().collection("trips")
                                    .document(trip.getId())
                                    .delete()
                                    .addOnSuccessListener(unused -> {
                                        tripList.remove(position);
                                        notifyItemRemoved(position);
                                    });
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        } else {
            holder.btnFavorite.setVisibility(View.VISIBLE);
            holder.btnJoin.setVisibility(View.VISIBLE);
            holder.btnDelete.setVisibility(View.GONE);
        }

        String userId = user.getUid();

        db.collection("users").document(userId)
                .collection("favorites").document(trip.getId())
                .get().addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        holder.btnFavorite.setImageResource(R.drawable.ic_heart_filled);
                    } else {
                        holder.btnFavorite.setImageResource(R.drawable.ic_heart_outline);
                    }
                });

        holder.btnFavorite.setOnClickListener(v -> {
            DocumentReference favRef = db.collection("users").document(userId)
                    .collection("favorites").document(trip.getId());

            favRef.get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    favRef.delete();
                    holder.btnFavorite.setImageResource(R.drawable.ic_heart_outline);
                } else {
                    favRef.set(new HashMap<>());
                    holder.btnFavorite.setImageResource(R.drawable.ic_heart_filled);
                }
            });
        });

        db.collection("users").document(userId)
                .collection("joinedTrips").document(trip.getId())
                .get().addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        holder.btnJoin.setText("Un-Join");
                    } else {
                        holder.btnJoin.setText("Join");
                    }
                });

        holder.btnJoin.setOnClickListener(v -> {
            holder.btnJoin.setEnabled(false);
            DocumentReference joinRef = db.collection("users").document(userId)
                    .collection("joinedTrips").document(trip.getId());
            DocumentReference tripRef = db.collection("trips").document(trip.getId());

            joinRef.get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    // Un-Join
                    joinRef.delete().addOnSuccessListener(unused -> {
                        tripRef.update("currentParticipants", FieldValue.increment(-1));
                        trip.setCurrentParticipants(trip.getCurrentParticipants() - 1); // Update local model
                        notifyItemChanged(holder.getAdapterPosition()); // Refresh item immediately
                        holder.btnJoin.setText("Join");
                        holder.btnJoin.setEnabled(true);
                    });
                } else {
                    // Join
                    HashMap<String, Object> joinData = new HashMap<>();
                    joinData.put("timestamp", FieldValue.serverTimestamp());

                    joinRef.set(joinData).addOnSuccessListener(unused -> {
                        tripRef.update("currentParticipants", FieldValue.increment(1));
                        trip.setCurrentParticipants(trip.getCurrentParticipants() + 1); // Update local model
                        notifyItemChanged(holder.getAdapterPosition()); // Refresh item immediately
                        holder.btnJoin.setText("Un-Join");
                        holder.btnJoin.setEnabled(true);
                        Toast.makeText(context, "You have joined the trip!", Toast.LENGTH_SHORT).show();
                        sendNotificationToAdmin(trip.getSubject());
                    }).addOnFailureListener(e -> {
                        holder.btnJoin.setEnabled(true);
                        Toast.makeText(context, "Failed to join trip.", Toast.LENGTH_SHORT).show();
                    });
                }
            });
        });

        DocumentReference tripRef = db.collection("trips").document(trip.getId());
        tripRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(DocumentSnapshot snapshot, FirebaseFirestoreException e) {
                if (e != null || snapshot == null || !snapshot.exists()) {
                    return;
                }

                Long updatedCount = snapshot.getLong("currentParticipants");
                if (updatedCount != null) {
                    trip.setCurrentParticipants(updatedCount);
                    holder.tvDates.setText("From " + trip.getStartDate() + " to " + trip.getEndDate() +
                            "\nParticipants: " + trip.getCurrentParticipants() + "/" + trip.getParticipants());
                }
            }
        });
    }

    private void sendNotificationToAdmin(String tripSubject) {
        ApiService apiService = RetrofitClient.getRetrofitInstance().create(ApiService.class);

        String adminSecret = context.getSharedPreferences("AdminPrefs", Context.MODE_PRIVATE)
                .getString("adminSecret", "");

        NotificationRequest notificationRequest = new NotificationRequest(
                "New Join Request",
                user.getEmail() + " has joined: " + tripSubject,
                "admin_topic",
                adminSecret
        );

        apiService.sendNotification(notificationRequest).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    Log.d("TripAdapter", "Notification sent to admin.");
                } else {
                    Log.e("TripAdapter", "Failed to send notification: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                Log.e("TripAdapter", "Notification error: " + t.getMessage());
            }
        });
    }

    @Override
    public int getItemCount() {
        return tripList.size();
    }

    public static class TripViewHolder extends RecyclerView.ViewHolder {
        TextView tvSubject, tvVenue, tvDates;
        Button btnDelete, btnJoin;
        ImageButton btnFavorite;
        LinearLayout imagePreviewLayout;

        public TripViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSubject = itemView.findViewById(R.id.tvTripSubject);
            tvVenue = itemView.findViewById(R.id.tvTripVenue);
            tvDates = itemView.findViewById(R.id.tvTripDates);
            imagePreviewLayout = itemView.findViewById(R.id.imagePreviewLayout);
            btnDelete = itemView.findViewById(R.id.btnTripDelete);
            btnFavorite = itemView.findViewById(R.id.btnFavorite);
            btnJoin = itemView.findViewById(R.id.btnJoin);
        }
    }

    private void showDetailDialog(Trip trip) {
        View view = LayoutInflater.from(context).inflate(R.layout.trip_detail_dialog, null);

        ((TextView) view.findViewById(R.id.tvDetailSubject)).setText(trip.getSubject());
        ((TextView) view.findViewById(R.id.tvDetailDates)).setText("From " + trip.getStartDate() + " to " + trip.getEndDate());
        ((TextView) view.findViewById(R.id.tvDetailVenue)).setText("Venue: " + trip.getVenue());
        ((TextView) view.findViewById(R.id.tvDetailActivities)).setText("Activities: " + trip.getActivities());
        ((TextView) view.findViewById(R.id.tvDetailParticipants)).setText("Participants: " + trip.getParticipants());
        ((TextView) view.findViewById(R.id.tvDetailTransportation)).setText("Transport: " + trip.getTransportation());
        ((TextView) view.findViewById(R.id.tvDetailDeadline)).setText("Deadline: " + trip.getDeadline());
        ((TextView) view.findViewById(R.id.tvDetailContact)).setText("Contact: " + trip.getContact());
        ((TextView) view.findViewById(R.id.tvDetailRemark)).setText("Remark: " + trip.getRemark());

        LinearLayout imageContainer = view.findViewById(R.id.imageDetailContainer);
        imageContainer.removeAllViews();
        List<String> imageBase64List = trip.getImageBase64List();
        if (imageBase64List != null && !imageBase64List.isEmpty()) {
            imageContainer.setVisibility(View.VISIBLE);
            for (String base64 : imageBase64List) {
                try {
                    byte[] decodedBytes = Base64.decode(base64, Base64.DEFAULT);
                    ImageView imageView = new ImageView(context);
                    imageView.setLayoutParams(new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    ));
                    imageView.setAdjustViewBounds(true);
                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    imageView.setImageBitmap(android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length));
                    imageView.setPadding(0, 8, 0, 8);
                    imageContainer.addView(imageView);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else {
            imageContainer.setVisibility(View.GONE);
        }

        new android.app.AlertDialog.Builder(context)
                .setTitle("Trip Details")
                .setView(view)
                .setPositiveButton("Close", null)
                .show();
    }
}
