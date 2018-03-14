import {filterOutProps} from 'Utils/ComponentUtils';
import { alert, confirm } from 'Utils/Platform';
import { broadcast } from 'Utils/StaticDataUtils';
import {ActionThunk} from "Utils/ActionCreatorUtils";
import {User, UserPreferences, PreferenceScope, UserWithPrefs, UserPredicate} from "Utils/WdkUser";
import {RecordInstance} from "Utils/WdkModel";
import { State as PasswordStoreState } from 'Views/User/Password/UserPasswordChangeStore';
import { State as ProfileStoreState, UserProfileFormData } from 'Views/User/Profile/UserProfileStore';
import { transitionToInternalPage, transitionToExternalPage } from 'Core/ActionCreators/RouterActionCreators';
import { default as WdkService, ServiceConfig } from 'Utils/WdkService';

// actions to update true user and preferences
export type UserUpdateAction = {
  type: "user/user-update",
  payload: {
    user: User;
  }
}
export type PreferenceUpdateAction = {
  type: 'user/preference-update',
  payload: UserPreferences
}
export type PreferencesUpdateAction = {
  type: 'user/preferences-update',
  payload: UserPreferences
}
type PrefAction = PreferenceUpdateAction|PreferencesUpdateAction;

// actions to manage the user profile/registration forms
export type ProfileFormUpdateAction = {
  type: 'user/profile-form-update',
  payload: {
    user: User
  }
}
export type ProfileFormSubmissionStatusAction = {
  type: 'user/profile-form-submission-status',
  payload: {
    formStatus: ProfileStoreState['formStatus'];
    errorMessage: string | undefined;
  }
}

export type ClearRegistrationFormAction = {
  type: 'user/clear-registration-form'
}

// actions to manage user password form
export type PasswordFormUpdateAction = {
  type: 'user/password-form-update',
  payload: PasswordStoreState['passwordForm'];
}
export type PasswordFormSubmissionStatusAction = {
  type: 'user/password-form-submission-status',
  payload: {
    formStatus: PasswordStoreState['formStatus'];
    errorMessage: string | undefined;
  }
}

// actions to manage user password reset form
export type ResetPasswordUpdateEmailAction = {
  type: 'user/reset-password-email-update',
  payload: string
}
export type ResetPasswordSubmissionStatusAction = {
  type: 'user/reset-password-submission-status',
  payload : {
    success: boolean,
    message?: string
  }
}

// actions related to login
export type ShowLoginModalAction = {
  type: 'user/show-login-modal',
  payload: {
    destination: string;
  }
}
export type LoginDismissedAction = {
  type: 'user/login-dismissed'
}
export type LoginErrorAction = {
  type: 'user/login-error',
  payload: {
    message: string;
  }
}

export type LoginRedirectAction = {
  type: 'user/login-redirect',
}
export type LogoutRedirectAction = {
  type: 'user/logout-redirect',
}

// basket actions
export type BasketStatusLoadingAction = {
  type: 'user/basket-status-loading',
  payload: {
    record: RecordInstance
  }
}
export type BasketStatusReceivedAction = {
  type: 'user/basket-status-received',
  payload: {
    record: RecordInstance;
    status: boolean;
  }
}
export type BasketStatusErrorAction = {
  type: 'user/basket-status-error',
  payload: {
    record: RecordInstance,
    error: Error
  }
}

// favorites actions
export type FavoritesStatusLoadingAction = {
  type: 'user/favorites-status-loading',
  payload: { record: RecordInstance }
}
export type FavoritesStatusReceivedAction = {
  type: 'user/favorites-status-received',
  payload: { record: RecordInstance, id?: number }
}
export type FavoritesStatusErrorAction = {
  type: 'user/favorites-status-error',
  payload: { record: RecordInstance, error: Error }
}

/**
 * Fetches the current user.  If the user passes the predicate, transitions to
 * the passed path.  Optional external param lets caller specify if path is
 * internal or external, defaulting to false (internal).
 */
export function conditionallyTransition(test: UserPredicate, path: string, external: boolean = false): ActionThunk<never> {
  return function run(dispatch, { wdkService }) {
    wdkService.getCurrentUser().then(user => {
      if (test(user)) {
        dispatch(external ?
          transitionToExternalPage(path) :
          transitionToInternalPage(path));
      }
    });
  };
}

/**
 * Merge supplied key-value pair with user preferences and update
 * on the server.
 */
export function updateUserPreference(scope: PreferenceScope, key: string, value: string): ActionThunk<PreferenceUpdateAction> {
  return function run(dispatch, { wdkService }) {
    let updatePromise = wdkService.updateCurrentUserPreference(scope, key, value);
    return dispatch(sendPrefUpdateOnCompletion(updatePromise,
        'user/preference-update', { [scope]: { [key]: value } } as UserPreferences) as Promise<PreferenceUpdateAction>);
  };
};

export function updateUserPreferences(newPreferences: UserPreferences): ActionThunk<PreferencesUpdateAction> {
  return function run(dispatch, { wdkService }) {
    let updatePromise = wdkService.updateCurrentUserPreferences(newPreferences);
    return dispatch(sendPrefUpdateOnCompletion(updatePromise,
        'user/preferences-update', newPreferences) as Promise<PreferencesUpdateAction>);
  };
};

function sendPrefUpdateOnCompletion(
  promise: Promise<UserPreferences>,
  actionName: PreferenceUpdateAction['type']|PreferencesUpdateAction['type'],
  payload: UserPreferences
): Promise<PreferenceUpdateAction|PreferencesUpdateAction> {
    let prefUpdater = function() {
      return broadcast({
        type: actionName,
        payload: payload
      }) as PreferenceUpdateAction|PreferencesUpdateAction;
    };
    return promise.then(
      () => {
        return prefUpdater();
      },
      (error) => {
        console.error(error.response);
        // update stores anyway; not a huge deal if preference doesn't make it to server
        return prefUpdater();
      }
    );
};

function createProfileFormStatusAction(status: string, errorMessage?: string) {
  return createFormStatusAction('user/profile-form-submission-status', status, errorMessage) as ProfileFormSubmissionStatusAction;
}

function createPasswordFormStatusAction(status: string, errorMessage?: string) {
  return createFormStatusAction('user/password-form-submission-status', status, errorMessage) as PasswordFormSubmissionStatusAction;
}

function createFormStatusAction(actionType: string, status: string, errorMessage?: string) {
  return {
    type: actionType,
    payload: {
      formStatus: status,
      errorMessage: errorMessage
    }
  }
}

/** Save user profile to DB */
type SubmitProfileFormType = ActionThunk<UserUpdateAction|PreferencesUpdateAction|ProfileFormSubmissionStatusAction>;
export function submitProfileForm(user: UserProfileFormData): SubmitProfileFormType {
  return function run(dispatch, { wdkService }) {
    dispatch(createProfileFormStatusAction('pending'));
    let trimmedUser = <UserProfileFormData>filterOutProps(user, ["isGuest", "id", "confirmEmail", "preferences"]);
    let userPromise = wdkService.updateCurrentUser(trimmedUser);
    let prefPromise = wdkService.updateCurrentUserPreferences(user.preferences as UserPreferences); // should never be null by this point
    return dispatch(Promise.all([userPromise, prefPromise])
      .then(() => {
        // success; update user first, then prefs, then status in ProfileViewStore
        dispatch(broadcast({
          type: 'user/user-update',
          // NOTE: this prop name should be the same as that used in StaticDataActionCreator for 'user'
          // NOTE2: not all user props were sent to update but all should remain EXCEPT 'confirmEmail' and 'preferences'
          payload: { user: filterOutProps(user, ["confirmEmail", "preferences"]) as User }
        }) as UserUpdateAction);
        dispatch(broadcast({
          type: 'user/preferences-update',
          payload: user.preferences as UserPreferences
        }) as PreferencesUpdateAction);
        return createProfileFormStatusAction('success');
      })
      .catch((error) => {
        console.error(error.response);
        return createProfileFormStatusAction('error', error.response);
      }));
  };
};

/** Register user */
type SubmitRegistrationFormType = ActionThunk<ProfileFormSubmissionStatusAction|ClearRegistrationFormAction>;
export function submitRegistrationForm (formData: UserProfileFormData): SubmitRegistrationFormType {
  return function run(dispatch, { wdkService }) {
    dispatch(createProfileFormStatusAction('pending'));
    let trimmedUser = <User>filterOutProps(formData, ["isGuest", "id", "preferences", "confirmEmail"]);
    let registrationData: UserWithPrefs = {
      user: trimmedUser,
      preferences: formData.preferences as UserPreferences
    }
    wdkService.createNewUser(registrationData)
      .then(responseData => {
        // success; clear the form in case user wants to register another user
        dispatch(broadcast({ type: 'user/clear-registration-form' }) as ClearRegistrationFormAction);
        // add success message to top of page
        dispatch(createProfileFormStatusAction('success'));
      })
      .catch((error) => {
        console.error(error.response);
        dispatch(createProfileFormStatusAction('error', error.response));
      });
  };
};

/** Update user profile present in the form (unsaved changes) */
export function updateProfileForm(user: User): ProfileFormUpdateAction {
  return {
    type: 'user/profile-form-update',
    payload: {user}
  }
};

/** Save new password to DB */
export function savePassword(oldPassword: string, newPassword: string): ActionThunk<PasswordFormSubmissionStatusAction> {
  return function run(dispatch, { wdkService }) {
    dispatch(createPasswordFormStatusAction('pending'));
    wdkService.updateCurrentUserPassword(oldPassword, newPassword)
      .then(() => {
        dispatch(createPasswordFormStatusAction('success'));
      })
      .catch((error) => {
        console.error(error.response);
        dispatch(createPasswordFormStatusAction('error', error.response));
      });
  };
};

/** Update change password form data (unsaved changes) */
export function updateChangePasswordForm(formData: PasswordStoreState['passwordForm']): PasswordFormUpdateAction {
  return {
    type: 'user/password-form-update',
    payload: formData
  }
};

export function updatePasswordResetEmail(emailText: string): ResetPasswordUpdateEmailAction {
  return {
    type: 'user/reset-password-email-update',
    payload: emailText
  };
};

function createResetPasswordStatusAction(message?: string): ResetPasswordSubmissionStatusAction {
  return {
    type: 'user/reset-password-submission-status',
    payload: {
      success: (message ? false : true),
      message: message
    }
  }
};

export function submitPasswordReset(email: string): ActionThunk<ResetPasswordSubmissionStatusAction> {
  return function run(dispatch, { wdkService, transitioner }) {
    dispatch(createResetPasswordStatusAction("Submitting..."));
    wdkService.resetUserPassword(email).then(
        () => {
          // clear form for next visitor to this page
          dispatch(createResetPasswordStatusAction(undefined));
          // transition to user message page
          transitioner.transitionToInternalPage('/user/message/password-reset-successful');
        },
        error => {
          dispatch(createResetPasswordStatusAction(error.response || error.message));
        }
    );
  };
};

// Session management action creators and helpers
// ----------------------------------------------

/**
 * Show a warning that user must be logged in for feature
 */
export function showLoginWarning(attemptedAction: string, destination?: string): ActionThunk<never|ShowLoginModalAction> {
  return function(dispatch) {
    confirm(
      'Login Required',
      'To ' + attemptedAction + ', you must be logged in. Would you like to login now?'
    ).then(confirmed => {
      if (confirmed) dispatch(showLoginForm(destination));
    });
  }
};

/**
 * Show the login form based on config
 */
export function showLoginForm(destination = window.location.href): ActionThunk<never|ShowLoginModalAction> {
  return function(dispatch, {wdkService}) {
    wdkService.getConfig().then(config => {
      config.authentication.method
      let { oauthClientId, oauthClientUrl, oauthUrl, method } = config.authentication;
      if (method === 'OAUTH2') {
        performOAuthLogin(destination, wdkService, oauthClientId, oauthClientUrl, oauthUrl);
      }
      else { // USER_DB
        dispatch({
          type: 'user/show-login-modal',
          payload: { destination }
        });
      }
    });
  };
};

export function hideLoginForm(): LoginDismissedAction {
  return {
    type: 'user/login-dismissed'
  }
}

export function submitLoginForm(email: string, password: string, destination: string): ActionThunk<never|LoginErrorAction> {
  return (dispatch, { wdkService }) => {
    wdkService.tryLogin(email, password, destination)
      .then(response => {
        if (response.success) {
          window.location.assign(response.redirectUrl);
        }
        else {
          dispatch(loginErrorReceived(response.message));
        }
      })
      .catch(error => {
        dispatch(loginErrorReceived("There was an error submitting your credentials.  Please try again later."));
      });
  };
}

export function loginErrorReceived(message: string): LoginErrorAction {
  return {
    type: 'user/login-error',
    payload: { message }
  }
}

function performOAuthLogin(destination: string, wdkService: WdkService,
  oauthClientId: string, oauthClientUrl: string, oauthUrl: string) {
  wdkService.getOauthStateToken()
    .then((response) => {
      let googleSpecific = (oauthUrl.indexOf("google") != -1);
      let [ redirectUrl, authEndpoint ] = googleSpecific ?
        [ oauthClientUrl + '/login', "auth" ] : // hacks to conform to google OAuth2 API
        [ oauthClientUrl + '/login?redirectUrl=' + encodeURIComponent(destination), "authorize" ];

      let finalOauthUrl = oauthUrl + "/" + authEndpoint + "?" +
        "response_type=code&" +
        "scope=" + encodeURIComponent("openid email") + "&" +
        "state=" + encodeURIComponent(response.oauthStateToken) + "&" +
        "client_id=" + oauthClientId + "&" +
        "redirect_uri=" + encodeURIComponent(redirectUrl);

      window.location.assign(finalOauthUrl);
    })
    .catch(error => {
      alert("Unable to fetch your WDK state token.", "Please check your internet connection.");
      throw error;
    });
}

function logout(): ActionThunk<never> {
  return function run(dispatch, { wdkService }) {
    wdkService.getConfig().then(config => {
      let { oauthClientId, oauthClientUrl, oauthUrl, method } = config.authentication;
      let logoutUrl = oauthClientUrl + '/logout';
      if (method === 'OAUTH2') {
        let googleSpecific = (oauthUrl.indexOf("google") != -1);
        // don't log user out of google, only the eupath oauth server
        let nextPage = (googleSpecific ? logoutUrl :
          oauthUrl + "/logout?redirect_uri=" + encodeURIComponent(logoutUrl));
        window.location.assign(nextPage);
      }
      else {
        window.location.assign(logoutUrl);
      }
    });
  };
};

export function showLogoutWarning(): ActionThunk<never> {
  return function(dispatch) {
    confirm(
      'Are you sure you want to logout?',
      'Note: You must log out of other EuPathDB sites separately'
    ).then(confirmed => {
      if (confirmed) dispatch(logout());
    });
  }
};

//----------------------------------
// Basket action creators and helpers
// ----------------------------------

type BasketAction = BasketStatusLoadingAction | BasketStatusErrorAction | BasketStatusReceivedAction

/**
 * @param {Record} record
 */
export function loadBasketStatus(record: RecordInstance): ActionThunk<BasketAction> {
  //if (user.isGuest) return basketAction(record, false);
  return function run(dispatch, { wdkService }) {
    return dispatch(setBasketStatus(
      record,
      wdkService.getBasketStatus(record.recordClassName, [record]).then(response => response[0])
    ));
  };
};

/**
 * @param {User} user
 * @param {Record} record
 * @param {Boolean} status
 */
export function updateBasketStatus(user: User, record: RecordInstance, status: boolean): ActionThunk<BasketAction|ShowLoginModalAction|never> {
  if (user.isGuest) return showLoginWarning('use baskets');
  return function run(dispatch, { wdkService }) {
    return dispatch(setBasketStatus(
      record,
      wdkService.updateBasketStatus(status, record.recordClassName, [record]).then(response => status)
    ));
  };
};

/**
 * @param {Record} record
 * @param {Promise<boolean>} basketStatusPromise
 */
let setBasketStatus = (record: RecordInstance, basketStatusPromise: Promise<boolean>): ActionThunk<BasketAction> => {
  return function run(dispatch) {
    dispatch({
      type: 'user/basket-status-loading',
      payload: { record }
    });
    return basketStatusPromise.then(
      status => {
        dispatch({
          type: 'user/basket-status-received',
          payload: {record, status}
        })
      },
      error => {
        dispatch({
          type: 'user/basket-status-error',
          payload: { record, error }
        })
      }
    );
  };
};


// Favorites action creators and helpers
// -------------------------------------

type FavoriteAction = FavoritesStatusErrorAction | FavoritesStatusLoadingAction | FavoritesStatusReceivedAction

/** Create favorites action */
/**
 * @param {Record} record
 */
export function loadFavoritesStatus(record: RecordInstance): ActionThunk<FavoriteAction> {
  //if (user.isGuest) return favoritesAction(record, false);
  return function run(dispatch, { wdkService }) {
    return dispatch(setFavoritesStatus(
      record,
      wdkService.getFavoriteId(record)
    ));
  };
};

export function removeFavorite(record: RecordInstance, favoriteId: number): ActionThunk<FavoriteAction> {
  return function run(dispatch, { wdkService }) {
    return dispatch(setFavoritesStatus(
      record,
      wdkService.deleteFavorite(favoriteId)
    ));
  };
};

export function addFavorite(user: User, record: RecordInstance): ActionThunk<FavoriteAction|ShowLoginModalAction|never> {
  if (user.isGuest) {
    return showLoginWarning('use favorites');
  }
  return function run(dispatch, { wdkService }) {
    return dispatch(setFavoritesStatus(
      record,
      wdkService.addFavorite(record)
    ));
  };
};

/**
 * @param {Record} record
 * @param {Promise<Boolean>} statusPromise
 */
function setFavoritesStatus(record: RecordInstance, statusPromise: Promise<number|undefined>): ActionThunk<FavoriteAction> {
  return function run(dispatch) {
    dispatch({
      type: 'user/favorites-status-loading',
      payload: { record }
    });
    return statusPromise.then(
      id => {
        dispatch({
          type: 'user/favorites-status-received',
          payload: {record, id}
        })
      },
      error => {
        dispatch({
          type: 'user/favorites-status-error',
          payload: { record, error }
        })
      }
    );
  };
};
