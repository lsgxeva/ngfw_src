import uvm.i18n_helper
import gettext
import reports.node.untangle_base_spam_blocker

_ = uvm.i18n_helper.get_translation('untangle').lgettext

reports.engine.register_node(reports.node.untangle_base_spam_blocker.SpamBaseNode('untangle-node-spam-blocker', 'Spam Blocker', 'spam_blocker', 'SpamBlocker', _('Spam'), _('Clean'), _("Hourly Spam Rate"), _("Spam Rate"), _("Top Ten Spammed")))
