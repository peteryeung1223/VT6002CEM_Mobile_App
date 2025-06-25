package com.app.letstravel;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class TravelNewsActivity extends AppCompatActivity {

    private RecyclerView recyclerViewNews;
    private NewsAdapter newsAdapter;
    private ArrayList<NewsModel> newsList = new ArrayList<>();
    private SwipeRefreshLayout swipeRefreshLayout;

    private static final String BASE_URL = "http://peter.serveblog.net:3000/"; // Your backend server URL

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_travel_news);

        recyclerViewNews = findViewById(R.id.recyclerViewNews);
        recyclerViewNews.setLayoutManager(new LinearLayoutManager(this));
        newsAdapter = new NewsAdapter(this, newsList);
        recyclerViewNews.setAdapter(newsAdapter);

        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        swipeRefreshLayout.setOnRefreshListener(this::fetchTripNewsApiKey);

        fetchTripNewsApiKey();
    }

    private void fetchTripNewsApiKey() {
        swipeRefreshLayout.setRefreshing(true);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        ApiService apiService = retrofit.create(ApiService.class);
        Call<ApiKeyResponse> call = apiService.getTripNewsApiKey();

        call.enqueue(new Callback<ApiKeyResponse>() {
            @Override
            public void onResponse(Call<ApiKeyResponse> call, Response<ApiKeyResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String apiKey = response.body().getApiKey();
                    fetchTravelNews(apiKey);
                } else {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(TravelNewsActivity.this, "Failed to fetch API Key", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiKeyResponse> call, Throwable t) {
                swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(TravelNewsActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchTravelNews(String apiKey) {
        new Thread(() -> {
            try {
                String apiUrl = "https://gnews.io/api/v4/search?q=travel&lang=en&max=10&token=" + apiKey;

                URL url = new URL(apiUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line);
                }
                reader.close();

                JSONObject jsonObject = new JSONObject(result.toString());
                JSONArray articles = jsonObject.getJSONArray("articles");

                ArrayList<NewsModel> fetchedNews = new ArrayList<>();
                for (int i = 0; i < articles.length(); i++) {
                    JSONObject article = articles.getJSONObject(i);
                    String title = article.getString("title");
                    String content = article.getString("description");
                    if (content.length() > 50) {
                        content = content.substring(0, Math.min(content.length(), 200)) + "...";
                    }
                    String imageUrl = article.optString("image");

                    fetchedNews.add(new NewsModel(title, content, imageUrl));
                }

                runOnUiThread(() -> {
                    newsList.clear();
                    newsList.addAll(fetchedNews);
                    newsAdapter.notifyDataSetChanged();
                    swipeRefreshLayout.setRefreshing(false);
                });

            } catch (Exception e) {
                Log.e("TravelNewsActivity", "Error fetching news", e);
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(this, "Failed to fetch news", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
}
