package com.alorma.github.ui.fragment.detail.repo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.TextView;

import com.alorma.github.R;
import com.alorma.github.UrlsManager;
import com.alorma.github.cache.CacheWrapper;
import com.alorma.github.sdk.bean.dto.response.Repo;
import com.alorma.github.sdk.bean.dto.response.User;
import com.alorma.github.sdk.bean.dto.response.UserType;
import com.alorma.github.sdk.bean.info.RepoInfo;
import com.alorma.github.sdk.services.client.GithubClient;
import com.alorma.github.sdk.services.repo.GetReadmeContentsClient;
import com.alorma.github.sdk.services.repo.actions.CheckRepoStarredClient;
import com.alorma.github.sdk.services.repo.actions.CheckRepoWatchedClient;
import com.alorma.github.sdk.services.repo.actions.StarRepoClient;
import com.alorma.github.sdk.services.repo.actions.UnstarRepoClient;
import com.alorma.github.sdk.services.repo.actions.UnwatchRepoClient;
import com.alorma.github.sdk.services.repo.actions.WatchRepoClient;
import com.alorma.github.ui.activity.ForksActivity;
import com.alorma.github.ui.activity.OrganizationActivity;
import com.alorma.github.ui.activity.ProfileActivity;
import com.alorma.github.ui.activity.RepoDetailActivity;
import com.alorma.github.ui.listeners.TitleProvider;
import com.alorma.github.ui.utils.UniversalImageLoaderUtils;
import com.alorma.github.utils.TimeUtils;
import com.clean.presenter.Presenter;
import com.clean.presenter.RepositoryPresenter;
import com.mikepenz.iconics.IconicsDrawable;
import com.mikepenz.iconics.typeface.IIcon;
import com.mikepenz.octicons_typeface_library.Octicons;

import java.text.DecimalFormat;
import java.text.NumberFormat;

import rx.Observer;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;


/**
 * Created by Bernat on 01/01/2015.
 */
public class RepoAboutFragment extends Fragment
        implements TitleProvider, BranchManager, BackManager {

    private static final String REPO_INFO = "REPO_INFO";
    public static final int PLACEHOLDER_ICON_SIZE = 20;

    private View author;
    private Integer futureSubscribersCount;
    private Integer futureStarredCount;

    private RepoInfo repoInfo;
    private Repo currentRepo;
    private WebView htmlContentView;
    private ImageView profileIcon;

    private TextView starredPlaceholder;
    private TextView watchedPlaceholder;
    private TextView forkPlaceHolder;

    private TextView authorName;
    private View fork;
    private TextView forkOfTextView;
    private TextView createdAtTextView;

    private View loadingHtml;

    private Boolean repoStarred = null;
    Observer<Boolean> startObserver = new Observer<Boolean>() {
        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {
            repoStarred = false;
            changeStarView();
        }

        @Override
        public void onNext(Boolean aBoolean) {
            repoStarred = aBoolean;
            changeStarView();
        }
    };

    private Boolean repoWatched = null;
    Observer<Boolean> watchObserver = new Observer<Boolean>() {
        @Override
        public void onCompleted() {

        }

        @Override
        public void onError(Throwable e) {
            repoWatched = false;
            changeWatchView();
        }

        @Override
        public void onNext(Boolean aBoolean) {
            repoWatched = aBoolean;
            changeWatchView();
        }
    };

    public static RepoAboutFragment newInstance(RepoInfo repoInfo) {
        Bundle bundle = new Bundle();
        bundle.putParcelable(REPO_INFO, repoInfo);

        RepoAboutFragment f = new RepoAboutFragment();
        f.setArguments(bundle);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        return inflater.inflate(R.layout.repo_overview_fragment, null, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        author = view.findViewById(R.id.author);
        profileIcon = (ImageView) author.findViewById(R.id.profileIcon);
        authorName = (TextView) author.findViewById(R.id.authorName);
        loadingHtml = view.findViewById(R.id.htmlLoading);

        htmlContentView = (WebView) view.findViewById(R.id.htmlContentView);

        fork = view.findViewById(R.id.fork);
        forkOfTextView = (TextView) fork.findViewById(R.id.forkOf);

        createdAtTextView = (TextView) view.findViewById(R.id.createdAt);

        starredPlaceholder = (TextView) view.findViewById(R.id.starredPlaceholder);
        watchedPlaceholder = (TextView) view.findViewById(R.id.watchedPlaceHolder);
        forkPlaceHolder = (TextView) view.findViewById(R.id.forkPlaceHolder);

        starredPlaceholder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (repoStarred != null) {
                    changeStarStatus();
                }
            }
        });

        watchedPlaceholder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (repoWatched != null) {
                    changeWatchedStatus();
                }
            }
        });

        forkPlaceHolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (repoInfo != null) {
                    Intent intent = ForksActivity.launchIntent(v.getContext(), repoInfo);
                    startActivity(intent);
                }
            }
        });

        fork.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentRepo != null && currentRepo.parent != null) {
                    RepoInfo repoInfo = new RepoInfo();
                    repoInfo.owner = currentRepo.parent.owner.login;
                    repoInfo.name = currentRepo.parent.name;
                    if (!TextUtils.isEmpty(currentRepo.default_branch)) {
                        repoInfo.branch = currentRepo.default_branch;
                    }

                    Intent intent = RepoDetailActivity.createLauncherIntent(getActivity(), repoInfo);
                    startActivity(intent);
                }
            }
        });

        author.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentRepo != null && currentRepo.owner != null) {
                    if (currentRepo.owner.type == UserType.User) {
                        Intent intent = ProfileActivity.createLauncherIntent(getActivity(), currentRepo.owner);
                        startActivity(intent);
                    } else if (currentRepo.owner.type == UserType.Organization) {
                        Intent intent =
                                OrganizationActivity.launchIntent(getActivity(), currentRepo.owner.login);
                        startActivity(intent);
                    }
                }
            }
        });

        getReadmeContent();
    }

    @Override
    public int getTitle() {
        return R.string.overview_fragment_title;
    }

    @Override
    public IIcon getTitleIcon() {
        return Octicons.Icon.oct_info;
    }

    protected void loadArguments() {
        if (getArguments() != null) {
            repoInfo = getArguments().getParcelable(REPO_INFO);
        }
    }

    public void setRepository(Repo repository) {
        this.currentRepo = repository;
        if (isAdded()) {
            getReadme();
            getStarWatchData();
            setData();
            getReadmeContent();
        }
    }

    private void getReadmeContent() {
        if (repoInfo == null) {
            loadArguments();
        }

        String cachedReadme = CacheWrapper.getReadme(repoInfo.toString());
        if (cachedReadme != null) {
            onReadmeLoaded(cachedReadme);
        }
        getReadme();
    }

    private void getReadme() {
        loadReadme(new GetReadmeContentsClient(getActivity(), repoInfo));
    }

    private void loadReadme(GetReadmeContentsClient repoMarkdownClient) {
        repoMarkdownClient.observable()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<String>() {
                    @Override
                    public void onCompleted() {
                        if (currentRepo != null) {
                            setData();
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        if (currentRepo != null && !TextUtils.isEmpty(currentRepo.description)) {
                            onReadmeLoaded(currentRepo.description);
                        } else {
                            loadingHtml.setVisibility(View.GONE);
                        }
                    }

                    @Override
                    public void onNext(String htmlContent) {
                        onReadmeLoaded(htmlContent);
                    }
                });
    }

    private void onReadmeLoaded(String htmlContent) {
        if (htmlContent != null && htmlContentView != null) {

            htmlContentView.getSettings().setUseWideViewPort(false);
            htmlContentView.setWebViewClient(new WebViewClient() {
                @Override
                public boolean shouldOverrideUrlLoading(WebView view, String url) {
                    Intent intent = new UrlsManager(getActivity()).checkUri(Uri.parse(url));
                    if (intent != null) {
                        startActivity(intent);
                        return true;
                    } else {
                        return false;
                    }
                }
            });
            htmlContentView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null);

            loadingHtml.setVisibility(View.GONE);

            CacheWrapper.setReadme(repoInfo.toString(), htmlContent);
        }
    }

    private void setData() {
        if (getActivity() != null) {
            if (this.currentRepo != null) {
                User owner = this.currentRepo.owner;
                UniversalImageLoaderUtils.loadUserAvatar(profileIcon, owner);
                authorName.setText(owner.login);

                if (this.currentRepo.parent != null) {
                    fork.setVisibility(View.VISIBLE);
                    forkOfTextView.setCompoundDrawables(getIcon(Octicons.Icon.oct_repo_forked, 24), null, null, null);
                    forkOfTextView.setText(String.format("%s/%s", this.currentRepo.parent.owner.login,
                            this.currentRepo.parent.name));
                }

                createdAtTextView.setCompoundDrawables(getIcon(Octicons.Icon.oct_clock, 24), null, null,
                        null);
                createdAtTextView.setText(
                        TimeUtils.getDateToText(getActivity(), this.currentRepo.created_at,
                                R.string.created_at));

                changeStarView();
                changeWatchView();

                setStarsCount(currentRepo.stargazers_count);

                setWatchersCount(currentRepo.subscribers_count);

                forkPlaceHolder.setText(String.valueOf(placeHolderNum(this.currentRepo.forks_count)));

                forkPlaceHolder.setCompoundDrawables(
                        getIcon(Octicons.Icon.oct_repo_forked, PLACEHOLDER_ICON_SIZE), null, null, null);
            }
        }
    }

    private void setStarsCount(int stargazers_count) {
        starredPlaceholder.setText(
                String.valueOf(placeHolderNum(stargazers_count)));
    }

    private void setWatchersCount(int subscribers_count) {
        watchedPlaceholder.setText(
                String.valueOf(placeHolderNum(subscribers_count)));
    }

    private String placeHolderNum(int value) {
        NumberFormat decimalFormat = new DecimalFormat();
        return decimalFormat.format(value);
    }

    private IconicsDrawable getIcon(IIcon icon, int sizeDp) {
        return new IconicsDrawable(getActivity(), icon).colorRes(R.color.primary).sizeDp(sizeDp);
    }

    @Override
    public void setCurrentBranch(String branch) {
        if (getActivity() != null) {
            repoInfo.branch = branch;
            loadReadme(new GetReadmeContentsClient(getActivity(), repoInfo));
        }
    }

    @Override
    public boolean onBackPressed() {
        return true;
    }

    private void starAction(GithubClient<Boolean> starClient) {
        starClient.observable().observeOn(AndroidSchedulers.mainThread()).subscribe(startObserver);
    }

    private void watchAction(GithubClient<Boolean> starClient) {
        starClient.observable().observeOn(AndroidSchedulers.mainThread()).subscribe(watchObserver);
    }

    protected void getStarWatchData() {
        starAction(
                new CheckRepoStarredClient(getActivity(), currentRepo.owner.login, currentRepo.name));

        watchAction(
                new CheckRepoWatchedClient(getActivity(), currentRepo.owner.login, currentRepo.name));
    }

    private void changeStarStatus() {
        if (repoStarred) {
            futureStarredCount = currentRepo.stargazers_count - 1;
            starAction(new UnstarRepoClient(getActivity(), currentRepo.owner.login, currentRepo.name));
        } else {
            futureStarredCount = currentRepo.stargazers_count + 1;
            starAction(new StarRepoClient(getActivity(), currentRepo.owner.login, currentRepo.name));
        }
    }

    private void changeWatchedStatus() {
        if (repoWatched) {
            futureSubscribersCount = currentRepo.subscribers_count - 1;
            watchAction(new UnwatchRepoClient(getActivity(), currentRepo.owner.login, currentRepo.name));
        } else {
            futureSubscribersCount = currentRepo.subscribers_count + 1;
            watchAction(new WatchRepoClient(getActivity(), currentRepo.owner.login, currentRepo.name));
        }
    }

    private void changeStarView() {
        if (getActivity() != null) {
            IconicsDrawable drawable =
                    new IconicsDrawable(getActivity(), Octicons.Icon.oct_star).sizeDp(PLACEHOLDER_ICON_SIZE);
            if (repoStarred != null && repoStarred) {
                drawable.colorRes(R.color.primary);
            } else {
                drawable.colorRes(R.color.icons);
            }
            starredPlaceholder.setCompoundDrawables(drawable, null, null, null);

            if (futureStarredCount != null) {
                setStarsCount(futureStarredCount);
                currentRepo.stargazers_count = futureStarredCount;
            }
        }
    }

    private void changeWatchView() {
        if (getActivity() != null) {
            IconicsDrawable drawable =
                    new IconicsDrawable(getActivity(), Octicons.Icon.oct_eye).sizeDp(PLACEHOLDER_ICON_SIZE);
            if (repoWatched != null && repoWatched) {
                drawable.colorRes(R.color.primary);
            } else {
                drawable.colorRes(R.color.icons);
            }
            watchedPlaceholder.setCompoundDrawables(drawable, null, null, null);

            if (futureSubscribersCount != null) {
                setWatchersCount(futureSubscribersCount);
                currentRepo.subscribers_count = futureSubscribersCount;
            }
        }
    }
}
