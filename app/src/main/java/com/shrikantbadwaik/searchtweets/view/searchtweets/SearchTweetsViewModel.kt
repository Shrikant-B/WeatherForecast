package com.shrikantbadwaik.searchtweets.view.searchtweets

import android.app.Application
import android.arch.lifecycle.AndroidViewModel
import android.arch.lifecycle.MutableLiveData
import android.databinding.ObservableArrayList
import android.databinding.ObservableBoolean
import com.shrikantbadwaik.searchtweets.data.remote.CallbackObserverWrapper
import com.shrikantbadwaik.searchtweets.data.repository.Repository
import com.shrikantbadwaik.searchtweets.domain.model.Tweet
import com.shrikantbadwaik.searchtweets.domain.model.Tweets
import com.shrikantbadwaik.searchtweets.domain.model.TwitterAccessToken
import com.shrikantbadwaik.searchtweets.domain.util.AppUtil
import com.shrikantbadwaik.searchtweets.domain.util.Constants
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Function
import io.reactivex.schedulers.Schedulers
import javax.inject.Inject

class SearchTweetsViewModel @Inject constructor(
    private val ctx: Application, private val repository: Repository
) : AndroidViewModel(ctx) {
    private var disposable: Disposable? = null
    private val dialogStateLiveData: MutableLiveData<String> = MutableLiveData()
    private val apiResultLiveData: MutableLiveData<String> = MutableLiveData()
    private var apiErrorsLiveData: MutableLiveData<String> = MutableLiveData()
    private val tweetsLiveData: MutableLiveData<ArrayList<Tweet>> = MutableLiveData()
    private var tweetsObservable: ObservableArrayList<Tweet> = ObservableArrayList()
    private val loading: ObservableBoolean = ObservableBoolean(false)
    private val filter: ObservableBoolean = ObservableBoolean(false)

    override fun onCleared() {
        super.onCleared()
        disposable?.dispose()
    }

    fun getDialogStateLiveData() = dialogStateLiveData

    fun getApiResultLiveData() = apiResultLiveData

    fun getApiErrorLiveData() = apiErrorsLiveData

    fun getTweetsLiveData() = tweetsLiveData

    fun setTweetsObservable(tweets: ArrayList<Tweet>?) {
        tweets?.let {
            if (it.isNotEmpty()) {
                tweetsObservable.clear()
                tweetsObservable.addAll(it)
            }
        }
    }

    fun getTweetsObservable() = tweetsObservable

    fun setLoading(state: Boolean) {
        loading.set(state)
    }

    fun isLoading(): ObservableBoolean = loading

    fun isFilter(): ObservableBoolean = filter

    fun onFilterButtonClicked() {
        dialogStateLiveData.value = Constants.DialogState.SORT_TWEETS_DIALOG.name
    }

    fun getMostRecentTweets(query: String?) {
        if (query.isNullOrEmpty()) {
            dialogStateLiveData.value = Constants.DialogState.QUERY_EMPTY_ERROR_DIALOG.name
        } else {
            if (repository.getAccessToken().isNullOrEmpty()) {
                generateAccessToken(query)
            } else searchMostRecentTweets(query)
        }
    }

    private fun generateAccessToken(query: String) {
        if (AppUtil.isDeviceOnline(ctx)) {
            setLoading(true)
            disposable = repository.generateAccessToken()
                .flatMap(object : Function<TwitterAccessToken, Observable<Tweets>> {
                    override fun apply(token: TwitterAccessToken): Observable<Tweets> {
                        repository.setAccessToken(token.accessToken)
                        return repository.mostRecentTweets(query)
                    }
                }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(object : CallbackObserverWrapper<Tweets>() {
                    override fun onSuccess(result: Tweets) {
                        setLoading(false)
                        apiResultLiveData.value = Constants.ApiResult.ON_SUCCESS.name
                        tweetsLiveData.value = result.tweets
                        filter.set(true)
                    }

                    override fun onFailure(error: String) {
                        setLoading(false)
                        apiResultLiveData.value = Constants.ApiResult.ON_FAILURE.name
                        apiErrorsLiveData.value = error
                        filter.set(false)
                    }

                    override fun onCompleted() {
                        setLoading(false)
                        apiResultLiveData.value = Constants.ApiResult.ON_COMPLETED.name
                        filter.set(true)
                    }
                })
        } else dialogStateLiveData.value = Constants.DialogState.DEVICE_OFFLINE_DIALOG.name
    }

    private fun searchMostRecentTweets(query: String) {
        setLoading(true)
        disposable = repository.mostRecentTweets(query)
            .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribeWith(object : CallbackObserverWrapper<Tweets>() {
                override fun onSuccess(result: Tweets) {
                    setLoading(false)
                    apiResultLiveData.value = Constants.ApiResult.ON_SUCCESS.name
                    tweetsLiveData.value = result.tweets
                    filter.set(true)
                }

                override fun onFailure(error: String) {
                    setLoading(false)
                    apiResultLiveData.value = Constants.ApiResult.ON_FAILURE.name
                    apiErrorsLiveData.value = error
                    filter.set(false)
                }

                override fun onCompleted() {
                    setLoading(false)
                    apiResultLiveData.value = Constants.ApiResult.ON_COMPLETED.name
                    filter.set(true)
                }
            })
    }

    fun sortTweets(sortBy: String?) {
        if (tweetsLiveData.value.isNullOrEmpty()) {
            dialogStateLiveData.value = Constants.DialogState.NO_TWEETS_TO_FILTER_DIALOG.name
            filter.set(false)
        } else {
            filter.set(true)
            when (sortBy) {
                Constants.SORT_BY_RETWEETS -> tweetsObservable.sortBy { it.retweetCount }
                Constants.SORT_BY_FAVORITES -> tweetsObservable.sortBy { it.favouriteCount }
                else -> tweetsObservable.sortWith(compareBy({ it.retweetCount }, { it.favouriteCount }))
            }
        }
    }
}